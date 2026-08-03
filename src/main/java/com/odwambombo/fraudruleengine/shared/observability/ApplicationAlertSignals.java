package com.odwambombo.fraudruleengine.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emits the stable, privacy-safe signals consumed by production alerting.
 *
 * <p>Exception messages and request details are deliberately excluded: they may
 * contain credentials, tokens, transaction payloads, or other customer data.</p>
 */
@Component
public final class ApplicationAlertSignals {

    static final String APPLICATION_EXCEPTION_MESSAGE =
            "APPLICATION_EXCEPTION Unhandled application exception";
    static final String FRAUD_PROCESSING_FAILURE_MESSAGE =
            "FRAUD_PROCESSING_FAILURE Fraud transaction processing failed";
    static final String AUTHENTICATION_FAILURE_MESSAGE =
            "AUTHENTICATION_FAILURE Authentication failed";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAlertSignals.class);

    private final Counter unhandledExceptionCounter;
    private final Counter fraudProcessingFailureCounter;
    private final Counter authenticationFailureCounter;

    public ApplicationAlertSignals(MeterRegistry meterRegistry) {
        this.unhandledExceptionCounter = meterRegistry.counter(
                "application.exceptions.unhandled"
        );
        this.fraudProcessingFailureCounter = meterRegistry.counter(
                "fraud.transactions.processing.failures"
        );
        this.authenticationFailureCounter = meterRegistry.counter(
                "security.authentication.failures"
        );
    }

    public void recordUnhandledApplicationException(Throwable exception) {
        unhandledExceptionCounter.increment();
        LOGGER.atError()
                .addKeyValue("alert.type", "application_exception")
                .addKeyValue("exception.type", resolveExceptionTypeName(exception))
                .log(APPLICATION_EXCEPTION_MESSAGE);
    }

    public void recordFraudProcessingFailure(Throwable exception) {
        fraudProcessingFailureCounter.increment();
        LOGGER.atError()
                .addKeyValue("alert.type", "fraud_processing_failure")
                .addKeyValue("exception.type", resolveExceptionTypeName(exception))
                .log(FRAUD_PROCESSING_FAILURE_MESSAGE);
    }

    public void recordAuthenticationFailure(Throwable exception) {
        recordAuthenticationFailure(resolveExceptionTypeName(exception));
    }

    public void recordLocalAuthenticationFailure() {
        recordAuthenticationFailure("InvalidLocalCredentials");
    }

    private void recordAuthenticationFailure(String authenticationFailureType) {
        authenticationFailureCounter.increment();
        LOGGER.atWarn()
                .addKeyValue("alert.type", "authentication_failure")
                .addKeyValue("authentication.failure.type", authenticationFailureType)
                .log(AUTHENTICATION_FAILURE_MESSAGE);
    }

    private static String resolveExceptionTypeName(Throwable throwable) {
        if (throwable == null) {
            return "Unknown";
        }
        final String simpleName = throwable.getClass().getSimpleName();
        return simpleName.isBlank() ? "Unknown" : simpleName;
    }
}
