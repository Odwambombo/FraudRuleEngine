package com.odwambombo.fraudruleengine.shared.security;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class FraudAuthorizationPolicyTest {

    private final FraudAuthorizationPolicy policy = new FraudAuthorizationPolicy();

    @Test
    void centralizesRolePermissionGrantsAndExternalCognitoNames() {
        assertThat(policy.rolesGranting(FraudPermission.TRANSACTION_WRITE))
                .containsExactly(FraudRole.OPERATOR, FraudRole.ADMIN);
        assertThat(policy.roleNamesGranting(FraudPermission.TRANSACTION_WRITE))
                .containsExactly("OPERATOR", "ADMIN");
        assertThat(policy.cognitoGroupNamesGranting(FraudPermission.ASSESSMENT_READ))
                .containsExactly("FRAUD_ANALYST", "FRAUD_AUDITOR", "FRAUD_ADMIN");
        assertThat(FraudRole.ADMIN.permissions())
                .containsExactlyInAnyOrder(FraudPermission.values());
        assertThat(FraudRole.fromCognitoGroup("FRAUD_OPERATOR"))
                .contains(FraudRole.OPERATOR);
        assertThat(FraudRole.fromCognitoGroup("UNKNOWN_GROUP")).isEmpty();
    }

    @Test
    void businessPermissionRequiresBothStablePermissionAndGrantingRoleAuthorities() {
        assertThat(policy.hasPermissionAndGrantingRole(
                authentication(
                        FraudPermission.TRANSACTION_WRITE.authority(),
                        FraudRole.OPERATOR.authority()
                ),
                FraudPermission.TRANSACTION_WRITE
        )).isTrue();

        assertThat(policy.hasPermissionAndGrantingRole(
                authentication(FraudPermission.TRANSACTION_WRITE.authority()),
                FraudPermission.TRANSACTION_WRITE
        )).isFalse();

        assertThat(policy.hasPermissionAndGrantingRole(
                authentication(
                        FraudPermission.TRANSACTION_WRITE.authority(),
                        FraudRole.ANALYST.authority()
                ),
                FraudPermission.TRANSACTION_WRITE
        )).isFalse();
    }

    @Test
    void permissionEnumContainsOnlyStableApplicationNamesAndAuthorities() {
        final FraudSecurityProperties securityProperties = new FraudSecurityProperties();
        securityProperties.getScopes().setTransactionWrite(
                "cognito-resource-server/transactions.write"
        );

        assertThat(FraudPermission.TRANSACTION_WRITE.name())
                .isEqualTo("TRANSACTION_WRITE");
        assertThat(FraudPermission.TRANSACTION_WRITE.authority())
                .isEqualTo("PERMISSION_TRANSACTION_WRITE");
    }

    @Test
    void cognitoScopesMustBePresentAndUniqueToPreventAuthorityEscalation() {
        final FraudSecurityProperties securityProperties = new FraudSecurityProperties();

        securityProperties.setEnabled(true);
        securityProperties.setProvider(FraudSecurityProperties.Provider.LOCAL);
        assertThat(securityProperties.areCognitoPermissionScopesConfigured()).isTrue();
        assertThat(securityProperties.arePermissionScopesUnique()).isTrue();

        securityProperties.setProvider(FraudSecurityProperties.Provider.COGNITO);
        assertThat(securityProperties.areCognitoPermissionScopesConfigured()).isFalse();
        assertThat(securityProperties.arePermissionScopesUnique()).isFalse();

        securityProperties.getScopes().setTransactionWrite("cognito.transactions.write");
        securityProperties.getScopes().setAssessmentRead("cognito.assessments.read");
        securityProperties.getScopes().setOperationsRead("cognito.operations.read");
        securityProperties.getScopes().setDocsRead("cognito.docs.read");
        assertThat(securityProperties.areCognitoPermissionScopesConfigured()).isTrue();
        assertThat(securityProperties.arePermissionScopesUnique()).isTrue();

        securityProperties.getScopes().setAssessmentRead("cognito.transactions.write");
        assertThat(securityProperties.arePermissionScopesUnique()).isFalse();
    }

    @Test
    void configuredCognitoScopesAreTrimmedAndNullSafeAtTheBoundary() {
        final FraudSecurityProperties securityProperties = new FraudSecurityProperties();

        securityProperties.getScopes().setTransactionWrite(
                "  cognito.transactions.write  "
        );

        assertThat(securityProperties.scopeFor(FraudPermission.TRANSACTION_WRITE))
                .isEqualTo("cognito.transactions.write");
        assertThat(securityProperties.scopeFor(FraudPermission.ASSESSMENT_READ))
                .isEmpty();
    }

    private static UsernamePasswordAuthenticationToken authentication(
            String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                "authorization-test",
                "not-used",
                List.of(authorities).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }
}
