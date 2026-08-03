package com.odwambombo.fraudruleengine.shared.exception;

import com.odwambombo.fraudruleengine.shared.observability.ApplicationAlertSignals;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private SimpleMeterRegistry meterRegistry;
    private ApiExceptionHandler exceptionHandler;
    private Logger logger;
    private Level originalLoggerLevel;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        exceptionHandler = new ApiExceptionHandler(
                Clock.fixed(Instant.parse("2026-08-02T10:15:30Z"), ZoneOffset.UTC),
                new ApplicationAlertSignals(meterRegistry)
        );
        logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
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
    void unexpectedExceptionsProduceAGenericResponseAndAnAlertSignal() {
        final var response = exceptionHandler.handleUnhandledException(
                new IllegalStateException("customer data must not be returned")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message())
                .isEqualTo("An unexpected error occurred while processing the request.");
        assertThat(meterRegistry.get("application.exceptions.unhandled").counter().count())
                .isEqualTo(1.0);

        final ILoggingEvent errorLog = logAppender.list.getFirst();
        assertThat(errorLog.getLevel()).isEqualTo(Level.ERROR);
        assertThat(errorLog.getFormattedMessage()).isEqualTo("API request failed");
        assertThat(errorLog.getFormattedMessage()).doesNotContain("customer data");
        assertThat(errorLog.getThrowableProxy()).isNull();
        assertStructuredValue(errorLog, "api.error.code", "INTERNAL_ERROR");
        assertStructuredValue(errorLog, "http.response.status_code", 500);
        assertStructuredValue(errorLog, "exception.type", "IllegalStateException");
    }

    @Test
    void authorizationDenialsRemainForbiddenAndDoNotRaiseAnExceptionAlert() {
        final var response = exceptionHandler.handleAccessDenied(
                new AccessDeniedException("denied")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(meterRegistry.get("application.exceptions.unhandled").counter().count())
                .isZero();
        assertThat(meterRegistry.get("security.authentication.failures").counter().count())
                .isZero();

        final ILoggingEvent warningLog = logAppender.list.getFirst();
        assertThat(warningLog.getLevel()).isEqualTo(Level.WARN);
        assertThat(warningLog.getFormattedMessage()).isEqualTo("API request rejected");
        assertThat(warningLog.getThrowableProxy()).isNull();
        assertStructuredValue(warningLog, "api.error.code", "ACCESS_DENIED");
        assertStructuredValue(warningLog, "http.response.status_code", 403);
        assertStructuredValue(warningLog, "exception.type", "AccessDeniedException");
    }

    private static void assertStructuredValue(
            ILoggingEvent loggingEvent,
            String key,
            Object value) {
        assertThat(loggingEvent.getKeyValuePairs())
                .anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo(key);
                    assertThat(pair.value).isEqualTo(value);
                });
    }
}
