package com.odwambombo.fraudruleengine.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoGrantedAuthoritiesConverterTest {

    private final FraudSecurityProperties securityProperties =
            new FraudSecurityProperties();
    private final CognitoGrantedAuthoritiesConverter converter =
            new CognitoGrantedAuthoritiesConverter(securityProperties);

    CognitoGrantedAuthoritiesConverterTest() {
        securityProperties.getScopes().setTransactionWrite("cognito.transactions.write");
        securityProperties.getScopes().setAssessmentRead("cognito.assessments.read");
        securityProperties.getScopes().setOperationsRead("cognito.operations.read");
        securityProperties.getScopes().setDocsRead("cognito.docs.read");
    }

    @Test
    void convertsScopesAndCognitoGroupsWithoutGrantingUnrelatedAuthorities() {
        final Jwt jwt = jwtBuilder()
                .claim("scope", "cognito.assessments.read cognito.transactions.write")
                .claim("cognito:groups", List.of("FRAUD_ANALYST", "FRAUD_ADMIN"))
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "PERMISSION_ASSESSMENT_READ",
                        "PERMISSION_TRANSACTION_WRITE",
                        "ROLE_ANALYST",
                        "ROLE_ADMIN"
                );
    }

    @Test
    void handlesAccessTokenWithoutGroups() {
        final Jwt jwt = jwtBuilder()
                .claim("scope", "cognito.operations.read")
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("PERMISSION_OPERATIONS_READ");
    }

    @Test
    void ignoresUnknownScopesAndCognitoGroups() {
        final Jwt jwt = jwtBuilder()
                .claim("scope", "unknown.scope cognito.transactions.write")
                .claim("cognito:groups", List.of("UNKNOWN_GROUP", "FRAUD_OPERATOR"))
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "PERMISSION_TRANSACTION_WRITE",
                        "ROLE_OPERATOR"
                );
    }

    @Test
    void doesNotAcceptLocalPermissionClaimsFromCognitoTokens() {
        final Jwt jwt = jwtBuilder()
                .claim("permissions", List.of("TRANSACTION_WRITE"))
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void mapsConfiguredHostedScopeToStablePermissionAuthority() {
        securityProperties.getScopes().setTransactionWrite(
                "  https://api.example.test/transactions.write  "
        );
        final Jwt jwt = jwtBuilder()
                .claim("scope", "https://api.example.test/transactions.write")
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("PERMISSION_TRANSACTION_WRITE");
    }

    private static Jwt.Builder jwtBuilder() {
        final Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("security-test")
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300));
    }
}
