package com.odwambombo.fraudruleengine.shared.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Central authorization policy shared by HTTP and method security. */
@Component("fraudAuthorizationPolicy")
public class FraudAuthorizationPolicy {

    /**
     * Requires both the requested permission and a role that grants it.
     * Intended for user-facing business operations.
     */
    public boolean hasPermissionAndGrantingRole(
            Authentication authentication,
            FraudPermission requiredPermission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        final Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        return authorities.contains(requiredPermission.authority())
                && rolesGranting(requiredPermission).stream()
                .map(FraudRole::authority)
                .anyMatch(authorities::contains);
    }

    AuthorizationManager<RequestAuthorizationContext> authorizationForBusinessPermission(
            FraudPermission requiredPermission) {
        final String[] roleAuthorities = rolesGranting(requiredPermission).stream()
                .map(FraudRole::authority)
                .toArray(String[]::new);
        return AuthorizationManagers.allOf(
                AuthorityAuthorizationManager.hasAuthority(requiredPermission.authority()),
                AuthorityAuthorizationManager.hasAnyAuthority(roleAuthorities)
        );
    }

    List<FraudRole> rolesGranting(FraudPermission requiredPermission) {
        return Arrays.stream(FraudRole.values())
                .filter(role -> role.grants(requiredPermission))
                .toList();
    }

    List<String> roleNamesGranting(FraudPermission requiredPermission) {
        return rolesGranting(requiredPermission).stream()
                .map(FraudRole::name)
                .toList();
    }

    List<String> cognitoGroupNamesGranting(FraudPermission requiredPermission) {
        return rolesGranting(requiredPermission).stream()
                .map(FraudRole::cognitoGroup)
                .toList();
    }
}
