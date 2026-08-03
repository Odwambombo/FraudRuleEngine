package com.odwambombo.fraudruleengine.shared.security;

import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentRepository;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:fraud_rule_engine_security;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "management.server.port=8080",
        "springdoc.api-docs.enabled=true",
        "fraud.security.enabled=true",
        "fraud.security.provider=cognito",
        "fraud.security.issuer-uri=https://issuer.example.test",
        "fraud.security.audience=fraud-rule-engine-test",
        "fraud.security.scopes.transaction-write=test.transactions.write",
        "fraud.security.scopes.assessment-read=test.assessments.read",
        "fraud.security.scopes.operations-read=test.operations.read",
        "fraud.security.scopes.docs-read=test.docs.read",
        "fraud.frontend.cognito.domain=https://fraud.auth.example.test/",
        "fraud.frontend.cognito.client-id=fraud-browser-client",
        "fraud.frontend.cognito.signup-enabled=true"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FraudSecurityIntegrationTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "fraud-rule-engine-test";
    private static final String TRANSACTION_WRITE = "test.transactions.write";
    private static final String ASSESSMENT_READ = "test.assessments.read";
    private static final String OPERATIONS_READ = "test.operations.read";
    private static final String DOCS_READ = "test.docs.read";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionEventRepository transactionRepository;

    @Autowired
    private FraudAssessmentRepository assessmentRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @TestBean(name = "fraudJwtDecoder", methodName = "testJwtDecoder", enforceOverride = true)
    private JwtDecoder fraudJwtDecoder;

    @BeforeEach
    void clearDatabase() {
        assessmentRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void protectedEndpointWithoutBearerTokenReturnsUnauthorized() throws Exception {
        final double failuresBefore = authenticationFailureCount();

        mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction()))
                .andExpect(status().isUnauthorized());

        assertThat(authenticationFailureCount()).isEqualTo(failuresBefore + 1.0);
    }

    @Test
    void authenticatedTokenWithoutRequiredScopeReturnsForbidden() throws Exception {
        final double failuresBefore = authenticationFailureCount();

        mockMvc.perform(post("/api/v1/transaction-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("assessment-read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction()))
                .andExpect(status().isForbidden());

        assertThat(authenticationFailureCount()).isEqualTo(failuresBefore);
    }

    @Test
    void permissionScopeWithoutRequiredBusinessRoleReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/transaction-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("transaction-write-no-role"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .header(HttpHeaders.AUTHORIZATION, bearer("assessment-read-wrong-role")))
                .andExpect(status().isForbidden());
    }

    @Test
    void transactionWriteAndAssessmentReadScopesAuthorizeTheirEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/transaction-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer("transaction-write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .header(HttpHeaders.AUTHORIZATION, bearer("assessment-read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .header(HttpHeaders.AUTHORIZATION, bearer("transaction-write")))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublicWhilePrometheusRequiresOperationsScope() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, bearer("assessment-read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, bearer("operations-read")))
                .andExpect(status().isOk());
    }

    @Test
    void frontendConfigurationIsPublicAndDoesNotExposeSecrets() throws Exception {
        mockMvc.perform(get("/api/v1/frontend-config"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.authenticationRequired").value(true))
                .andExpect(jsonPath("$.loginConfigured").value(true))
                .andExpect(jsonPath("$.signupEnabled").value(true))
                .andExpect(jsonPath("$.provider").value("cognito"))
                .andExpect(jsonPath("$.cognito.domain")
                        .value("https://fraud.auth.example.test"))
                .andExpect(jsonPath("$.cognito.clientId").value("fraud-browser-client"))
                .andExpect(jsonPath("$.permissions.transactionWrite.permission")
                        .value("TRANSACTION_WRITE"))
                .andExpect(jsonPath("$.permissions.transactionWrite.scope")
                        .value(TRANSACTION_WRITE))
                .andExpect(jsonPath("$.permissions.transactionWrite.roles[0]")
                        .value("FRAUD_OPERATOR"))
                .andExpect(jsonPath("$.permissions.assessmentRead.scope")
                        .value(ASSESSMENT_READ))
                .andExpect(jsonPath("$.permissions.assessmentRead.permission")
                        .value("ASSESSMENT_READ"))
                .andExpect(jsonPath("$.permissions.assessmentRead.roles[0]")
                        .value("FRAUD_ANALYST"));
    }

    @Test
    void cognitoClientIdCanSatisfyAudienceValidation() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, bearer("cognito-client-id")))
                .andExpect(status().isOk());
    }

    @Test
    void nonLocalDocumentationRequiresDocsPermissionAndDescribesApiSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("operations-read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("docs-read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Fraud Rule Engine API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
                        .value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/transaction-events'].post.summary"
                ).value("Assess a transaction event"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/transaction-events'].post.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/fraud-assessments'].get.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/frontend-config'].get.security"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/transaction-events'].post.responses['201']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/transaction-events'].post.responses['409']"
                ).exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "wrong-issuer",
            "wrong-audience",
            "id-token",
            "missing-token-use",
            "invalid"
    })
    void invalidIdentityTokensAreRejected(String token) throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUnknownRouteIsDeniedEvenWithEveryScope() throws Exception {
        mockMvc.perform(get("/not-a-real-route")
                        .header(HttpHeaders.AUTHORIZATION, bearer("all-scopes")))
                .andExpect(status().isForbidden());
    }

    private static JwtDecoder testJwtDecoder() {
        final OAuth2TokenValidator<Jwt> validator =
                FraudSecurityConfiguration.jwtValidator(ISSUER, AUDIENCE);
        return token -> {
            final Jwt jwt = jwtFor(token);
            final var validation = validator.validate(jwt);
            if (validation.hasErrors()) {
                throw new JwtValidationException("Test JWT validation failed", validation.getErrors());
            }
            return jwt;
        };
    }

    private double authenticationFailureCount() {
        return meterRegistry.get("security.authentication.failures").counter().count();
    }

    private static Jwt jwtFor(String token) {
        return switch (token) {
            case "transaction-write" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "access", List.of("FRAUD_OPERATOR"), TRANSACTION_WRITE);
            case "transaction-write-no-role" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "access", List.of(), TRANSACTION_WRITE);
            case "assessment-read" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "access", List.of("FRAUD_ANALYST"), ASSESSMENT_READ);
            case "assessment-read-wrong-role" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "access", List.of("FRAUD_OPERATOR"), ASSESSMENT_READ);
            case "operations-read" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "access", List.of(), OPERATIONS_READ);
            case "docs-read" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "access", List.of(), DOCS_READ);
            case "all-scopes" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "access", List.of("FRAUD_ADMIN"),
                    TRANSACTION_WRITE, ASSESSMENT_READ, OPERATIONS_READ, DOCS_READ);
            case "cognito-client-id" -> jwt(token, ISSUER, List.of(), AUDIENCE,
                    "access", List.of(), OPERATIONS_READ);
            case "wrong-issuer" -> jwt(token, "https://wrong-issuer.example.test",
                    List.of(AUDIENCE), null, "access", List.of(), OPERATIONS_READ);
            case "wrong-audience" -> jwt(token, ISSUER, List.of("another-api"), null,
                    "access", List.of(), OPERATIONS_READ);
            case "id-token" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    "id", List.of(), OPERATIONS_READ);
            case "missing-token-use" -> jwt(token, ISSUER, List.of(AUDIENCE), null,
                    null, List.of(), OPERATIONS_READ);
            default -> throw new BadJwtException("Unknown test token");
        };
    }

    private static Jwt jwt(
            String token,
            String issuer,
            List<String> audiences,
            String clientId,
            String tokenUse,
            List<String> groups,
            String... scopes) {
        final Instant now = Instant.now();
        final Jwt.Builder builder = Jwt.withTokenValue(token)
                .header("alg", "none")
                .header("typ", "JWT")
                .subject("security-test")
                .issuer(issuer)
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .claim("scope", String.join(" ", Arrays.asList(scopes)));
        if (tokenUse != null) {
            builder.claim("token_use", tokenUse);
        }
        if (!groups.isEmpty()) {
            builder.claim("cognito:groups", groups);
        }
        if (!audiences.isEmpty()) {
            builder.audience(audiences);
        }
        if (clientId != null) {
            builder.claim("client_id", clientId);
        }
        return builder.build();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sampleTransaction() {
        return """
                {
                  "eventId": "evt-security-10001",
                  "transactionId": "txn-security-50001",
                  "customerId": "cust-security-123",
                  "amount": 100.00,
                  "currency": "ZAR",
                  "category": "GROCERIES",
                  "transactionType": "CARD_PURCHASE",
                  "merchant": "Security Test Shop",
                  "country": "ZA",
                  "customerCountry": "ZA",
                  "transactionTime": "2026-07-27T10:00:00"
                }
                """;
    }
}
