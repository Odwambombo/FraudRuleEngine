package com.odwambombo.fraudruleengine.shared.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationAlertSignalsTest {

    private SimpleMeterRegistry meterRegistry;
    private ApplicationAlertSignals alertSignals;
    private Logger logger;
    private Level originalLoggerLevel;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        alertSignals = new ApplicationAlertSignals(meterRegistry);
        logger = (Logger) LoggerFactory.getLogger(ApplicationAlertSignals.class);
        originalLoggerLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logger.setLevel(originalLoggerLevel);
        logAppender.stop();
        meterRegistry.close();
    }

    @Test
    void emitsStablePrivacySafeMetricsAndLogEvents() {
        alertSignals.recordUnhandledApplicationException(
                new IllegalStateException("secret customer and transaction details")
        );
        alertSignals.recordFraudProcessingFailure(
                new IllegalArgumentException("secret transaction payload")
        );
        alertSignals.recordLocalAuthenticationFailure();

        assertThat(counter("application.exceptions.unhandled")).isEqualTo(1.0);
        assertThat(counter("fraud.transactions.processing.failures")).isEqualTo(1.0);
        assertThat(counter("security.authentication.failures")).isEqualTo(1.0);

        final List<ILoggingEvent> events = logAppender.list;
        assertThat(events).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "APPLICATION_EXCEPTION Unhandled application exception",
                        "FRAUD_PROCESSING_FAILURE Fraud transaction processing failed",
                        "AUTHENTICATION_FAILURE Authentication failed"
                );
        assertStructuredValue(events.get(0), "alert.type", "application_exception");
        assertStructuredValue(events.get(1), "alert.type", "fraud_processing_failure");
        assertStructuredValue(events.get(2), "alert.type", "authentication_failure");
        assertThat(events)
                .allSatisfy(event -> {
                    assertThat(event.getFormattedMessage()).doesNotContain("secret");
                    assertThat(event.getThrowableProxy()).isNull();
                });
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private static void assertStructuredValue(
            ILoggingEvent event,
            String key,
            String value) {
        assertThat(event.getKeyValuePairs())
                .anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo(key);
                    assertThat(pair.value).isEqualTo(value);
                });
    }
}
