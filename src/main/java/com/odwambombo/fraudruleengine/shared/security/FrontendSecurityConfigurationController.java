package com.odwambombo.fraudruleengine.shared.security;

import com.odwambombo.fraudruleengine.shared.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes non-secret browser authentication and permission configuration. */
@RestController
@RequestMapping("/api/v1/frontend-config")
@Tag(
        name = "Frontend configuration",
        description = "Publishes non-secret browser authentication and authorization settings."
)
public class FrontendSecurityConfigurationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            FrontendSecurityConfigurationController.class
    );

    private final FraudSecurityProperties securityProperties;
    private final FraudFrontendProperties frontendProperties;
    private final FraudAuthorizationPolicy authorizationPolicy;

    FrontendSecurityConfigurationController(
            FraudSecurityProperties securityProperties,
            FraudFrontendProperties frontendProperties,
            FraudAuthorizationPolicy authorizationPolicy) {
        this.securityProperties = securityProperties;
        this.frontendProperties = frontendProperties;
        this.authorizationPolicy = authorizationPolicy;
    }

    @GetMapping
    @Operation(
            summary = "Get browser authentication configuration",
            description = "Public, non-cacheable bootstrap configuration for the web console. "
                    + "It contains provider identifiers and authorization requirements, never secrets."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The current non-secret frontend configuration.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = FrontendSecurityConfigurationResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "The client exceeded the configured API rate limit.",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<FrontendSecurityConfigurationResponse> getFrontendSecurityConfiguration() {
        final String clientId = StringUtils.hasText(frontendProperties.getClientId())
                ? frontendProperties.getClientId().trim()
                : normalize(securityProperties.getAudience());
        final String domain = normalizeDomain(frontendProperties.getDomain());
        final boolean cognitoLoginConfigured = StringUtils.hasText(clientId)
                && StringUtils.hasText(domain);
        final boolean localProvider = securityProperties.getProvider()
                == FraudSecurityProperties.Provider.LOCAL;
        final boolean loginConfigured = securityProperties.isEnabled()
                && (localProvider || cognitoLoginConfigured);

        final List<String> oauthScopes = localProvider
                ? List.of()
                : Stream.of(
                                "openid",
                                "profile",
                                "email",
                                securityProperties.scopeFor(
                                        FraudPermission.TRANSACTION_WRITE
                                ),
                                securityProperties.scopeFor(
                                        FraudPermission.ASSESSMENT_READ
                                )
                        )
                        .map(FrontendSecurityConfigurationController::normalize)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();

        final FrontendSecurityConfigurationResponse response =
                new FrontendSecurityConfigurationResponse(
                        securityProperties.isEnabled(),
                        loginConfigured,
                        !localProvider && frontendProperties.isSignupEnabled(),
                        localProvider ? "local" : "cognito",
                        new CognitoConfiguration(domain, clientId),
                        new LocalAuthenticationConfiguration("/api/v1/auth/login"),
                        oauthScopes,
                        new PermissionConfiguration(
                                buildPermissionRequirement(
                                        FraudPermission.TRANSACTION_WRITE,
                                        localProvider
                                ),
                                buildPermissionRequirement(
                                        FraudPermission.ASSESSMENT_READ,
                                        localProvider
                                )
                        )
                );
        LOGGER.atInfo()
                .addKeyValue("api.operation", "get_frontend_security_configuration")
                .addKeyValue("http.request.method", "GET")
                .addKeyValue("http.response.status_code", HttpStatus.OK.value())
                .addKeyValue("authentication.provider", response.provider())
                .addKeyValue("authentication.required", response.authenticationRequired())
                .log("Frontend security configuration API request completed");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    private PermissionRequirement buildPermissionRequirement(
            FraudPermission requiredPermission,
            boolean isLocalProvider) {
        final String externalScope = isLocalProvider
                ? ""
                : normalize(securityProperties.scopeFor(requiredPermission));
        return new PermissionRequirement(
                requiredPermission.name(),
                externalScope,
                isLocalProvider
                        ? authorizationPolicy.roleNamesGranting(requiredPermission)
                        : authorizationPolicy.cognitoGroupNamesGranting(requiredPermission)
        );
    }

    private static String normalize(String configuredValue) {
        return configuredValue == null ? "" : configuredValue.trim();
    }

    private static String normalizeDomain(String configuredDomain) {
        final String normalizedDomain = normalize(configuredDomain);
        return normalizedDomain.endsWith("/")
                ? normalizedDomain.substring(0, normalizedDomain.length() - 1)
                : normalizedDomain;
    }

    public record FrontendSecurityConfigurationResponse(
            boolean authenticationRequired,
            boolean loginConfigured,
            boolean signupEnabled,
            String provider,
            CognitoConfiguration cognito,
            LocalAuthenticationConfiguration local,
            List<String> oauthScopes,
            PermissionConfiguration permissions) {
    }

    public record CognitoConfiguration(String domain, String clientId) {
    }

    public record LocalAuthenticationConfiguration(String loginEndpoint) {
    }

    public record PermissionConfiguration(
            PermissionRequirement transactionWrite,
            PermissionRequirement assessmentRead) {
    }

    public record PermissionRequirement(
            String permission,
            String scope,
            List<String> roles) {

        public PermissionRequirement {
            roles = List.copyOf(roles);
        }
    }
}
