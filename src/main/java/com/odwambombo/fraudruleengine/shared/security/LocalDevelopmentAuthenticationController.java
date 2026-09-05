package com.odwambombo.fraudruleengine.shared.security;

import com.odwambombo.fraudruleengine.shared.observability.ApplicationAlertSignals;
import com.odwambombo.fraudruleengine.shared.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local-only username/password exchange. There is deliberately no signup endpoint. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(
        name = "Local authentication",
        description = "Developer-only token issuance; hosted environments use Amazon Cognito."
)
@Profile("!staging & !production & !migration")
@ConditionalOnProperty(
        prefix = "fraud.security",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnProperty(
        prefix = "fraud.security",
        name = "provider",
        havingValue = "local"
)
class LocalDevelopmentAuthenticationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            LocalDevelopmentAuthenticationController.class
    );

    private final LocalDevelopmentAuthenticationService authenticationService;
    private final ApplicationAlertSignals alertSignals;

    LocalDevelopmentAuthenticationController(
            LocalDevelopmentAuthenticationService authenticationService,
            ApplicationAlertSignals alertSignals) {
        this.authenticationService = authenticationService;
        this.alertSignals = alertSignals;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Issue a local development access token",
            description = "Exchanges a configured local username and password for a short-lived "
                    + "signed JWT. There is deliberately no local signup endpoint, and this "
                    + "operation is absent from staging and production."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A short-lived bearer access token and its granted authorities.",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The username or password field is blank or missing.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "The supplied credentials are invalid.",
                    content = @Content(schema = @Schema(implementation = LoginError.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "The client exceeded the local login rate limit.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        final LocalDevelopmentAuthenticationService.AuthenticationResult result =
                authenticationService.authenticateAndIssueAccessToken(
                        loginRequest.username(),
                        loginRequest.password()
                );
        if (result == null) {
            alertSignals.recordLocalAuthenticationFailure();
            return addNoStoreHeaders(ResponseEntity.status(HttpStatus.UNAUTHORIZED))
                    .body(new LoginError("invalid_credentials"));
        }
        LOGGER.atInfo()
                .addKeyValue("api.operation", "local_login")
                .addKeyValue("http.request.method", "POST")
                .addKeyValue("http.response.status_code", HttpStatus.OK.value())
                .addKeyValue("authentication.role_count", result.roles().size())
                .addKeyValue("authentication.permission_count", result.permissions().size())
                .log("Local authentication API request completed");
        return addNoStoreHeaders(ResponseEntity.ok())
                .body(new LoginResponse(
                        result.accessToken(),
                        "Bearer",
                        result.expiresIn(),
                        result.username(),
                        result.roles(),
                        result.permissions()
                ));
    }

    private static ResponseEntity.BodyBuilder addNoStoreHeaders(
            ResponseEntity.BodyBuilder responseBuilder) {
        return responseBuilder
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache");
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String username,
            List<String> roles,
            List<String> permissions) {
        LoginResponse {
            roles = List.copyOf(roles);
            permissions = List.copyOf(permissions);
        }
    }

    record LoginError(String error) {
    }
}
