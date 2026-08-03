package com.odwambombo.fraudruleengine.transaction.application;

import com.odwambombo.fraudruleengine.assessment.domain.MatchedRule;
import com.odwambombo.fraudruleengine.shared.exception.ProcessingTemporarilyUnavailableException;
import com.odwambombo.fraudruleengine.shared.observability.ApplicationAlertSignals;
import com.odwambombo.fraudruleengine.shared.persistence.DatabaseConstraintClassifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class TransactionProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionProcessingService.class);
    private static final int MAX_TRANSACTION_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_BACKOFF_MILLIS = 10;

    private final TransactionAssessmentProcessor assessmentProcessor;
    private final MeterRegistry meterRegistry;
    private final Counter processedCounter;
    private final Counter flaggedCounter;
    private final Counter replayedCounter;
    private final Timer processingTimer;
    private final ApplicationAlertSignals alertSignals;

    public TransactionProcessingService(TransactionAssessmentProcessor assessmentProcessor, MeterRegistry meterRegistry, ApplicationAlertSignals alertSignals) {
        this.assessmentProcessor = assessmentProcessor;
        this.meterRegistry = meterRegistry;
        this.alertSignals = alertSignals;
        this.processedCounter = meterRegistry.counter("fraud.transactions.processed");
        this.flaggedCounter = meterRegistry.counter("fraud.transactions.flagged");
        this.replayedCounter = meterRegistry.counter("fraud.transactions.idempotent.replayed");
        this.processingTimer = meterRegistry.timer("fraud.transactions.processing.duration");
    }

    public ProcessTransactionResult processTransaction(ProcessTransactionCommand transactionCommand) {
        final Timer.Sample timer = Timer.start(meterRegistry);
        try {
            final ProcessTransactionResult processingResult = processTransactionWithRetry(transactionCommand);
            recordProcessingMetrics(processingResult);
            logProcessingResult(processingResult);
            return processingResult;
        } catch (RuntimeException exception) {
            alertSignals.recordFraudProcessingFailure(exception);
            throw exception;
        } finally {
            timer.stop(processingTimer);
        }
    }

    private ProcessTransactionResult processTransactionWithRetry(ProcessTransactionCommand transactionCommand) {
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                return assessmentProcessor.assessTransaction(transactionCommand);
            } catch (DataIntegrityViolationException exception) {
                final boolean duplicateEvent = DatabaseConstraintClassifier.isCausedByConstraint(
                        exception,
                        DatabaseConstraintClassifier.EVENT_ID_UNIQUE_CONSTRAINT
                );
                if (!duplicateEvent || attempt == MAX_TRANSACTION_ATTEMPTS) {
                    throw exception;
                }
            } catch (TransientDataAccessException exception) {
                if (attempt == MAX_TRANSACTION_ATTEMPTS) {
                    throw new ProcessingTemporarilyUnavailableException(exception);
                }
                pauseBeforeRetry(attempt, exception);
            }
        }
        throw new IllegalStateException("Transaction retry loop completed unexpectedly");
    }

    private void pauseBeforeRetry(int failedAttempt, RuntimeException cause) {
        final long backoffMillis = INITIAL_RETRY_BACKOFF_MILLIS << (failedAttempt - 1);
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProcessingTemporarilyUnavailableException(cause);
        }
    }

    private void recordProcessingMetrics(ProcessTransactionResult processingResult) {
        if (!processingResult.created()) {
            replayedCounter.increment();
            return;
        }

        processedCounter.increment();

        if (processingResult.assessment().flagged()) {
            flaggedCounter.increment();
        }

        processingResult.assessment().matchedRules().stream()
                .map(MatchedRule::ruleCode)
                .forEach(ruleCode -> meterRegistry.counter("fraud.rules.matches", "rule", ruleCode).increment());
    }

    private void logProcessingResult(ProcessTransactionResult processingResult) {
        LOGGER.atInfo()
                .addKeyValue("fraud.assessment.id", processingResult.assessment().assessmentId())
                .addKeyValue("fraud.risk.score", processingResult.assessment().riskScore())
                .addKeyValue("fraud.risk.level", processingResult.assessment().riskLevel())
                .addKeyValue("fraud.flagged", processingResult.assessment().flagged())
                .addKeyValue("fraud.matched_rule.count", processingResult.assessment().matchedRules().size())
                .addKeyValue("transaction.created", processingResult.created())
                .log("Transaction assessment completed");
    }
}
