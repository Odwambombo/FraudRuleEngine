package com.odwambombo.fraudruleengine.shared.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Application roles and the permissions each role grants. */
public enum FraudRole {

    OPERATOR(
            "FRAUD_OPERATOR",
            EnumSet.of(FraudPermission.TRANSACTION_WRITE)
    ),
    ANALYST(
            "FRAUD_ANALYST",
            EnumSet.of(FraudPermission.ASSESSMENT_READ)
    ),
    AUDITOR(
            "FRAUD_AUDITOR",
            EnumSet.of(FraudPermission.ASSESSMENT_READ)
    ),
    ADMIN(
            "FRAUD_ADMIN",
            EnumSet.allOf(FraudPermission.class)
    );

    private static final String AUTHORITY_PREFIX = "ROLE_";

    private final String cognitoGroup;
    private final Set<FraudPermission> permissions;

    FraudRole(String cognitoGroup, EnumSet<FraudPermission> permissions) {
        this.cognitoGroup = cognitoGroup;
        this.permissions = Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public String cognitoGroup() {
        return cognitoGroup;
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public Set<FraudPermission> permissions() {
        return permissions;
    }

    public boolean grants(FraudPermission permission) {
        return permissions.contains(permission);
    }

    public static Optional<FraudRole> fromCognitoGroup(String cognitoGroup) {
        if (cognitoGroup == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(role -> role.cognitoGroup.equals(cognitoGroup))
                .findFirst();
    }
}
