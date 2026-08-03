package com.odwambombo.fraudruleengine.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Local-only credentials and token settings. This configuration is never loaded by hosted profiles. */
@ConfigurationProperties(prefix = "fraud.security.local-auth")
@Validated
class LocalDevelopmentAuthenticationProperties {

    private static final Duration MINIMUM_TOKEN_TTL = Duration.ofMinutes(1);
    private static final Duration MAXIMUM_TOKEN_TTL = Duration.ofHours(1);

    @NotBlank
    private String signingKey;

    @NotNull
    private Duration tokenTtl = Duration.ofMinutes(15);

    @Valid
    @NotEmpty
    private List<User> users = List.of();

    public String getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = List.copyOf(users);
    }

    @AssertTrue(message = "local JWT signing-key must contain at least 32 UTF-8 bytes")
    boolean isSigningKeyLongEnough() {
        return signingKey != null
                && signingKey.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    @AssertTrue(message = "local JWT token-ttl must be between 1 minute and 1 hour")
    boolean isTokenTtlShortLived() {
        return tokenTtl != null
                && tokenTtl.compareTo(MINIMUM_TOKEN_TTL) >= 0
                && tokenTtl.compareTo(MAXIMUM_TOKEN_TTL) <= 0;
    }

    @AssertTrue(message = "local authentication usernames must be unique")
    boolean areUsernamesUnique() {
        final Set<String> usernames = new HashSet<>();
        return users != null && users.stream()
                .map(User::getUsername)
                .allMatch(usernames::add);
    }

    static class User {

        @NotBlank
        private String username;

        @NotBlank
        @Size(min = 8)
        private String password;

        @NotEmpty
        private List<@NotNull FraudRole> roles = List.of();

        @NotEmpty
        private List<@NotNull FraudPermission> permissions = List.of();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<FraudRole> getRoles() {
            return roles;
        }

        public void setRoles(List<FraudRole> roles) {
            this.roles = List.copyOf(roles);
        }

        public List<FraudPermission> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<FraudPermission> permissions) {
            this.permissions = List.copyOf(permissions);
        }
    }
}
