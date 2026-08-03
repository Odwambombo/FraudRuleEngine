package com.odwambombo.fraudruleengine.shared.security;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

/** Authenticates configured development users and issues access tokens. */
final class LocalDevelopmentAuthenticationService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$VvN7DMC/dIHha9sfpx3M4.aZf0KTwG0mbI7SFPLUg9p6eRM0xckr2";

    private final Map<String, LocalUser> users;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtEncoder jwtEncoder;
    private final FraudSecurityProperties securityProperties;
    private final Clock clock;
    private final long tokenTtlSeconds;

    LocalDevelopmentAuthenticationService(
            LocalDevelopmentAuthenticationProperties localAuthenticationProperties,
            JwtEncoder jwtEncoder,
            FraudSecurityProperties securityProperties,
            Clock clock) {
        this.users = buildUsersByUsername(localAuthenticationProperties.getUsers());
        this.jwtEncoder = jwtEncoder;
        this.securityProperties = securityProperties;
        this.clock = clock;
        this.tokenTtlSeconds = localAuthenticationProperties.getTokenTtl().toSeconds();
    }

    AuthenticationResult authenticateAndIssueAccessToken(
            String username,
            String plaintextPassword) {
        final String normalizedUsername = username == null ? "" : username.trim();
        final LocalUser user = users.get(normalizedUsername);
        final String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
        final boolean passwordMatches = passwordEncoder.matches(
                plaintextPassword,
                passwordHash
        );
        if (user == null || !passwordMatches) {
            return null;
        }

        final Instant issuedAt = clock.instant();
        final Instant expiresAt = issuedAt.plusSeconds(tokenTtlSeconds);
        final List<String> roles = user.roles().stream()
                .map(FraudRole::name)
                .toList();
        final List<String> permissions = user.permissions().stream()
                .map(FraudPermission::name)
                .toList();
        final JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.getIssuerUri())
                .subject(user.username())
                .audience(List.of(securityProperties.getAudience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("token_use", "access")
                .claim("permissions", permissions)
                .claim("roles", roles)
                .build();
        final JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        final String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
        return new AuthenticationResult(
                accessToken,
                tokenTtlSeconds,
                user.username(),
                roles,
                permissions
        );
    }

    private Map<String, LocalUser> buildUsersByUsername(
            List<LocalDevelopmentAuthenticationProperties.User> configuredUsers) {
        final Map<String, LocalUser> result = new LinkedHashMap<>();
        for (LocalDevelopmentAuthenticationProperties.User configuredUser : configuredUsers) {
            final String username = configuredUser.getUsername().trim();
            result.put(username, new LocalUser(
                    username,
                    passwordEncoder.encode(configuredUser.getPassword()),
                    configuredUser.getRoles().stream().distinct().toList(),
                    configuredUser.getPermissions().stream().distinct().toList()
            ));
        }
        return Map.copyOf(result);
    }

    record AuthenticationResult(
            String accessToken,
            long expiresIn,
            String username,
            List<String> roles,
            List<String> permissions) {
        AuthenticationResult {
            roles = List.copyOf(roles);
            permissions = List.copyOf(permissions);
        }
    }

    private record LocalUser(
            String username,
            String passwordHash,
            List<FraudRole> roles,
            List<FraudPermission> permissions) {
    }
}
