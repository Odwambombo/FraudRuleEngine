package com.odwambombo.fraudruleengine.shared.exception;

public class ProcessingTemporarilyUnavailableException extends RuntimeException {

    public ProcessingTemporarilyUnavailableException(Throwable cause) {
        super("Transaction processing could not be completed after retrying.", cause);
    }
}
