package com.odwambombo.fraudruleengine.shared.security;

import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentRepository;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:fraud_rule_engine_local_auth;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "management.server.port=8080",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "fraud.security.enabled=true",
        "fraud.security.provider=local",
        "fraud.security.issuer-uri=http://localhost:8080",
        "fraud.security.audience=fraud-rule-engine-local-test",
        "fraud.security.local-auth.signing-key="
                + "test-only-local-jwt-signing-key-with-more-than-thirty-two-bytes",
        "fraud.security.local-auth.token-ttl=5m",
        "fraud.security.local-auth.users[0].username=test-admin",
        "fraud.security.local-auth.users[0].password=test-admin-password",
        "fraud.security.local-auth.users[0].roles=ADMIN",
        "fraud.security.local-auth.users[0].permissions="
                + "TRANSACTION_WRITE,ASSESSMENT_READ,OPERATIONS_READ,DOCS_READ",
        "fraud.security.local-auth.users[1].username=test-operator",
        "fraud.security.local-auth.users[1].password=test-operator-password",
        "fraud.security.local-auth.users[1].roles=OPERATOR",
        "fraud.security.local-auth.users[1].permissions=TRANSACTION_WRITE"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class LocalDevelopmentAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("fraudJwtDecoder")
    private JwtDecoder jwtDecoder;

    @Autowired
    private TransactionEventRepository transactionRepository;

    @Autowired
    private FraudAssessmentRepository assessmentRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void clearDatabase() {
        assessmentRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void frontendConfigurationAdvertisesOnlyTheLocalLoginEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/frontend-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticationRequired").value(true))
                .andExpect(jsonPath("$.loginConfigured").value(true))
                .andExpect(jsonPath("$.signupEnabled").value(false))
                .andExpect(jsonPath("$.provider").value("local"))
                .andExpect(jsonPath("$.local.loginEndpoint")
                        .value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.oauthScopes").isEmpty())
                .andExpect(jsonPath("$.permissions.transactionWrite.permission")
                        .value("TRANSACTION_WRITE"))
                .andExpect(jsonPath("$.permissions.transactionWrite.scope").value(""))
                .andExpect(jsonPath("$.permissions.transactionWrite.roles[0]")
                        .value("OPERATOR"))
                .andExpect(jsonPath("$.permissions.transactionWrite.roles[1]")
                        .value("ADMIN"))
                .andExpect(jsonPath("$.permissions.assessmentRead.permission")
                        .value("ASSESSMENT_READ"))
                .andExpect(jsonPath("$.permissions.assessmentRead.scope").value(""))
                .andExpect(jsonPath("$.permissions.assessmentRead.roles[0]")
                        .value("ANALYST"));
    }

    @Test
    void localApiDocumentationIsPublicWhileBusinessApisRemainProtected() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Fraud Rule Engine API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
                        .value("http"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/transaction-events'].post.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/transaction-events'].post.responses['429']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/frontend-config'].get.security"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/frontend-config'].get.responses['429']"
                ).exists());

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/fraud-assessments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCredentialsIssueAShortLivedSignedAccessToken() throws Exception {
        final MvcResult result = login("test-admin", "test-admin-password")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.username").value("test-admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.permissions[0]").value("TRANSACTION_WRITE"))
                .andExpect(jsonPath("$.scopes").doesNotExist())
                .andReturn();

        final String accessToken = accessToken(result);
        final var jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo("test-admin");
        assertThat(jwt.getAudience()).containsExactly("fraud-rule-engine-local-test");
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("access");
        assertThat(jwt.getClaimAsStringList("permissions"))
                .containsExactly(
                        "TRANSACTION_WRITE",
                        "ASSESSMENT_READ",
                        "OPERATIONS_READ",
                        "DOCS_READ"
                );
        assertThat(jwt.hasClaim("scope")).isFalse();
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("ADMIN");
        assertThat(jwt.hasClaim("cognito:groups")).isFalse();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void issuedAdminTokenCanCallTheProtectedControllers() throws Exception {
        final String token = accessToken(login("test-admin", "test-admin-password")
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/v1/transaction-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void localUserStillNeedsBothItsConfiguredPermissionAndRole() throws Exception {
        final String token = accessToken(login("test-operator", "test-operator-password")
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/v1/transaction-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCredentialsReturnAGenericUnauthorizedResponse() throws Exception {
        final double failuresBefore = authenticationFailureCount();

        login("test-admin", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.error").value("invalid_credentials"));

        login("not-a-user", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));

        assertThat(authenticationFailureCount()).isEqualTo(failuresBefore + 2.0);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username,
            String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginCredentials(
                        username,
                        password
                ))));
    }

    private String accessToken(MvcResult result) throws Exception {
        final JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        final String accessToken = body.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private double authenticationFailureCount() {
        return meterRegistry.get("security.authentication.failures").counter().count();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sampleTransaction() {
        return """
                {
                  "eventId": "evt-local-security-10001",
                  "transactionId": "txn-local-security-50001",
                  "customerId": "cust-local-security-123",
                  "amount": 100.00,
                  "currency": "ZAR",
                  "category": "GROCERIES",
                  "transactionType": "CARD_PURCHASE",
                  "merchant": "Local Security Test Shop",
                  "country": "ZA",
                  "customerCountry": "ZA",
                  "transactionTime": "2026-07-27T10:00:00"
                }
                """;
    }

    private record LoginCredentials(String username, String password) {
    }
}
