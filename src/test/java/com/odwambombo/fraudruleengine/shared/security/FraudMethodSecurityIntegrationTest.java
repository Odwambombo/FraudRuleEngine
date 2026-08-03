package com.odwambombo.fraudruleengine.shared.security;

import com.odwambombo.fraudruleengine.assessment.api.FraudAssessmentController;
import com.odwambombo.fraudruleengine.transaction.api.TransactionEventController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls proxied controller beans directly, bypassing the HTTP filter chain, so
 * failures here specifically prove that the method annotations are enforced.
 */
@SpringBootTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:fraud_rule_engine_method_security;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "fraud.security.enabled=true",
        "fraud.security.provider=cognito",
        "fraud.security.issuer-uri=https://issuer.example.test",
        "fraud.security.audience=fraud-rule-engine-test",
        "fraud.security.scopes.transaction-write=test.transactions.write",
        "fraud.security.scopes.assessment-read=test.assessments.read",
        "fraud.security.scopes.operations-read=test.operations.read",
        "fraud.security.scopes.docs-read=test.docs.read"
})
class FraudMethodSecurityIntegrationTest {

    @Autowired
    private TransactionEventController transactionController;

    @Autowired
    private FraudAssessmentController assessmentController;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void transactionMethodRejectsScopeWithoutConfiguredRole() {
        authenticate(
                FraudPermission.TRANSACTION_WRITE.authority(),
                FraudRole.ANALYST.authority()
        );

        assertThatThrownBy(() -> transactionController.processTransactionEvent(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assessmentMethodRejectsRoleWithoutConfiguredScope() {
        authenticate(FraudRole.ANALYST.authority());

        assertThatThrownBy(() -> assessmentController.search(
                null, null, null, null, null, 0, 20
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assessmentMethodRejectsScopeWithoutConfiguredRole() {
        authenticate(
                FraudPermission.ASSESSMENT_READ.authority(),
                FraudRole.OPERATOR.authority()
        );

        assertThatThrownBy(() -> assessmentController.search(
                null, null, null, null, null, 0, 20
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assessmentMethodAllowsConfiguredScopeAndRole() {
        authenticate(
                FraudPermission.ASSESSMENT_READ.authority(),
                FraudRole.AUDITOR.authority()
        );

        final var response = assessmentController.search(
                null, null, null, null, null, 0, 20
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isEmpty();
    }

    private static void authenticate(String... authorities) {
        final var grantedAuthorities = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        final var authentication = new UsernamePasswordAuthenticationToken(
                "method-security-test",
                "not-used",
                grantedAuthorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
