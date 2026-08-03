package com.odwambombo.fraudruleengine.transaction.application;

import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import com.odwambombo.fraudruleengine.shared.exception.ProcessingTemporarilyUnavailableException;
import com.odwambombo.fraudruleengine.shared.observability.ApplicationAlertSignals;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionProcessingServiceTest {

    @Mock
    private TransactionAssessmentProcessor assessmentProcessor;

    private SimpleMeterRegistry meterRegistry;
    private TransactionProcessingService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new TransactionProcessingService(
                assessmentProcessor,
                meterRegistry,
                new ApplicationAlertSignals(meterRegistry)
        );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void retriesTransientFailureInAFreshAttemptAndRecordsCommittedResultOnce() {
        final ProcessTransactionCommand command = command();
        final ProcessTransactionResult expected = result(true);
        when(assessmentProcessor.assessTransaction(command))
                .thenThrow(new TransientDataAccessResourceException("serialization failure"))
                .thenReturn(expected);

        final ProcessTransactionResult actual = service.processTransaction(command);

        assertSame(expected, actual);
        verify(assessmentProcessor, times(2)).assessTransaction(command);
        assertEquals(1.0, counter("fraud.transactions.processed"));
        assertEquals(0.0, counter("fraud.transactions.processing.failures"));
    }

    @Test
    void retriesNamedEventConstraintAndReturnsTheWinningReplay() {
        final ProcessTransactionCommand command = command();
        final ProcessTransactionResult expected = result(false);
        when(assessmentProcessor.assessTransaction(command))
                .thenThrow(new DataIntegrityViolationException(
                        "constraint uk_transaction_event_event_id"
                ))
                .thenReturn(expected);

        final ProcessTransactionResult actual = service.processTransaction(command);

        assertSame(expected, actual);
        verify(assessmentProcessor, times(2)).assessTransaction(command);
        assertEquals(1.0, counter("fraud.transactions.idempotent.replayed"));
        assertEquals(0.0, counter("fraud.transactions.processed"));
    }

    @Test
    void doesNotRetryUnrelatedIntegrityFailure() {
        final ProcessTransactionCommand command = command();
        final DataIntegrityViolationException failure =
                new DataIntegrityViolationException("constraint fk_other");
        when(assessmentProcessor.assessTransaction(command)).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> service.processTransaction(command)
                )
        );
        verify(assessmentProcessor).assessTransaction(command);
        assertEquals(1.0, counter("fraud.transactions.processing.failures"));
    }

    @Test
    void exposesStructuredRetryExhaustionAfterThreeTransientFailures() {
        final ProcessTransactionCommand command = command();
        when(assessmentProcessor.assessTransaction(command))
                .thenThrow(new TransientDataAccessResourceException("serialization failure"));

        assertThrows(
                ProcessingTemporarilyUnavailableException.class,
                () -> service.processTransaction(command)
        );

        verify(assessmentProcessor, times(3)).assessTransaction(command);
        assertEquals(1.0, counter("fraud.transactions.processing.failures"));
        assertEquals(0.0, counter("fraud.transactions.processed"));
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private ProcessTransactionCommand command() {
        return new ProcessTransactionCommand(
                "evt-retry",
                "txn-retry",
                "cust-retry",
                new BigDecimal("100.00"),
                "ZAR",
                "GROCERIES",
                "CARD_PURCHASE",
                "Grocery Store",
                "ZA",
                "ZA",
                LocalDateTime.of(2026, 7, 27, 12, 0)
        );
    }

    private ProcessTransactionResult result(boolean created) {
        return new ProcessTransactionResult(
                new FraudAssessment(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "evt-retry",
                        "txn-retry",
                        "cust-retry",
                        new BigDecimal("100.00"),
                        "ZAR",
                        "GROCERIES",
                        "CARD_PURCHASE",
                        "Grocery Store",
                        "ZA",
                        "ZA",
                        0,
                        RiskLevel.LOW,
                        false,
                        List.of(),
                        LocalDateTime.of(2026, 7, 27, 12, 0),
                        Instant.parse("2026-07-27T10:00:00Z")
                ),
                created
        );
    }
}
