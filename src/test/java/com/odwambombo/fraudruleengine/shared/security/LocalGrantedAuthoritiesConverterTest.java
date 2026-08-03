package com.odwambombo.fraudruleengine.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class LocalGrantedAuthoritiesConverterTest {

    private final LocalGrantedAuthoritiesConverter converter =
            new LocalGrantedAuthoritiesConverter();

    @Test
    void convertsTypedPermissionsAndConfiguredRoles() {
        final Jwt jwt = jwtBuilder()
                .claim("permissions", List.of("TRANSACTION_WRITE", "ASSESSMENT_READ"))
                .claim("roles", List.of("OPERATOR", "ANALYST"))
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "PERMISSION_TRANSACTION_WRITE",
                        "PERMISSION_ASSESSMENT_READ",
                        "ROLE_OPERATOR",
                        "ROLE_ANALYST"
                );
    }

    @Test
    void ignoresUnknownPermissionNamesAndExternalOauthScopes() {
        final Jwt jwt = jwtBuilder()
                .claim("permissions", List.of("UNKNOWN_PERMISSION", "OPERATIONS_READ"))
                .claim("scope", "cognito.transactions.write")
                .claim("roles", List.of("UNKNOWN_ROLE"))
                .claim("cognito:groups", List.of("FRAUD_ADMIN"))
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("PERMISSION_OPERATIONS_READ");
    }

    private static Jwt.Builder jwtBuilder() {
        final Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("local-security-test")
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300));
    }
}
