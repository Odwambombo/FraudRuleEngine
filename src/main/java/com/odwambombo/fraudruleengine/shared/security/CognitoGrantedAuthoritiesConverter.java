package com.odwambombo.fraudruleengine.shared.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Translates external OAuth scopes and Cognito groups into stable application
 * permission and role authorities. Unknown provider values are ignored.
 */
final class CognitoGrantedAuthoritiesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String SCOPE_CLAIM = "scope";
    private static final String ALTERNATIVE_SCOPE_CLAIM = "scp";
    private static final String COGNITO_GROUPS_CLAIM = "cognito:groups";

    private final FraudSecurityProperties securityProperties;

    CognitoGrantedAuthoritiesConverter(FraudSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        final Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        final Set<String> tokenScopes = Stream.concat(
                        extractClaimValues(jwt.getClaim(SCOPE_CLAIM)),
                        extractClaimValues(jwt.getClaim(ALTERNATIVE_SCOPE_CLAIM))
                )
                .collect(Collectors.toUnmodifiableSet());

        Arrays.stream(FraudPermission.values())
                .filter(permission -> tokenScopes.contains(
                        securityProperties.scopeFor(permission)
                ))
                .map(FraudPermission::authority)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        extractClaimValues(jwt.getClaim(COGNITO_GROUPS_CLAIM))
                .map(FraudRole::fromCognitoGroup)
                .flatMap(Optional::stream)
                .map(FraudRole::authority)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return List.copyOf(authorities);
    }

    private static Stream<String> extractClaimValues(Object rawClaimValue) {
        if (rawClaimValue instanceof String stringValue) {
            return splitClaimValues(stringValue);
        }
        if (rawClaimValue instanceof Collection<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .flatMap(CognitoGrantedAuthoritiesConverter::splitClaimValues);
        }
        return Stream.empty();
    }

    private static Stream<String> splitClaimValues(String rawClaimValue) {
        return Arrays.stream(rawClaimValue.trim().split("\\s+"))
                .filter(scope -> !scope.isBlank());
    }
}
