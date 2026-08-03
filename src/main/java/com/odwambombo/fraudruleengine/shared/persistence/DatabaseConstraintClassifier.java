package com.odwambombo.fraudruleengine.shared.persistence;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

public final class DatabaseConstraintClassifier {

    public static final String EVENT_ID_UNIQUE_CONSTRAINT = "uk_transaction_event_event_id";

    private DatabaseConstraintClassifier() {}

    public static boolean isCausedByConstraint(Throwable throwable, String expectedConstraintName) {
        Throwable currentCause = throwable;
        while (currentCause != null) {
            if (currentCause instanceof ConstraintViolationException constraintViolation
                    && expectedConstraintName.equalsIgnoreCase(
                    constraintViolation.getConstraintName()
            )) {
                return true;
            }
            final String exceptionMessage = currentCause.getMessage();
            if (exceptionMessage != null && exceptionMessage.toLowerCase(Locale.ROOT)
                    .contains(expectedConstraintName.toLowerCase(Locale.ROOT))) {
                return true;
            }
            currentCause = currentCause.getCause();
        }
        return false;
    }
}
