package com.odwambombo.fraudruleengine.shared.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Translates the typed claims issued by the local development token issuer. */
final class LocalGrantedAuthoritiesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        final Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        extractClaimValues(jwt.getClaim(PERMISSIONS_CLAIM))
                .map(LocalGrantedAuthoritiesConverter::parsePermission)
                .flatMap(Optional::stream)
                .map(FraudPermission::authority)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        extractClaimValues(jwt.getClaim(ROLES_CLAIM))
                .map(LocalGrantedAuthoritiesConverter::parseRole)
                .flatMap(Optional::stream)
                .map(FraudRole::authority)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return List.copyOf(authorities);
    }

    private static Optional<FraudPermission> parsePermission(String permissionName) {
        try {
            return Optional.of(FraudPermission.valueOf(permissionName));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<FraudRole> parseRole(String roleName) {
        try {
            return Optional.of(FraudRole.valueOf(roleName));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Stream<String> extractClaimValues(Object rawClaimValue) {
        if (rawClaimValue instanceof String stringValue) {
            return splitClaimValues(stringValue);
        }
        if (rawClaimValue instanceof Collection<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .flatMap(LocalGrantedAuthoritiesConverter::splitClaimValues);
        }
        return Stream.empty();
    }

    private static Stream<String> splitClaimValues(String rawClaimValue) {
        return Arrays.stream(rawClaimValue.trim().split("[\\s,]+"))
                .filter(permission -> !permission.isBlank());
    }
}
