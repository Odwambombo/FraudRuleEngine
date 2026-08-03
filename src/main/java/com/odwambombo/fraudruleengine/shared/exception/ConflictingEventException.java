package com.odwambombo.fraudruleengine.shared.exception;

public class ConflictingEventException extends RuntimeException {

    public ConflictingEventException(String eventId) {
        super(
                "Event ID '%s' was already used for a different transaction payload."
                        .formatted(eventId)
        );
    }
}
