package com.odwambombo.fraudruleengine.transaction.application;

import com.odwambombo.fraudruleengine.assessment.application.FraudAssessmentMapper;
import com.odwambombo.fraudruleengine.assessment.application.RiskScoringService;
import com.odwambombo.fraudruleengine.assessment.application.RiskScoringService.RiskDecision;
import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentEntity;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentRepository;
import com.odwambombo.fraudruleengine.rule.application.FraudRuleEngine;
import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.shared.exception.ConflictingEventException;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventEntity;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class TransactionAssessmentProcessor {

    private final TransactionEventRepository transactionRepository;
    private final FraudAssessmentRepository assessmentRepository;
    private final FraudRuleEngine ruleEngine;
    private final RiskScoringService riskScoringService;
    private final FraudProperties fraudProperties;
    private final Clock clock;


    public TransactionAssessmentProcessor(
            TransactionEventRepository transactionRepository,
            FraudAssessmentRepository assessmentRepository,
            FraudRuleEngine ruleEngine,
            RiskScoringService riskScoringService,
            FraudProperties fraudProperties,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.assessmentRepository = assessmentRepository;
        this.ruleEngine = ruleEngine;
        this.riskScoringService = riskScoringService;
        this.fraudProperties = fraudProperties;
        this.clock = clock;
    }

    @Transactional(
            isolation = Isolation.SERIALIZABLE,
            propagation = Propagation.REQUIRES_NEW
    )
    public ProcessTransactionResult assessTransaction(ProcessTransactionCommand transactionCommand) {
        return assessmentRepository.findByTransactionEvent_EventId(transactionCommand.eventId())
                .map(existingAssessment -> resolveIdempotentReplay(
                        existingAssessment,
                        transactionCommand
                ))
                .orElseGet(() -> assessNewTransaction(transactionCommand));
    }

    private ProcessTransactionResult resolveIdempotentReplay(
            FraudAssessmentEntity existingAssessment,
            ProcessTransactionCommand transactionCommand) {
        final TransactionEventEntity existingEvent = existingAssessment.getTransactionEvent();
        if (!payloadMatches(existingEvent, transactionCommand)) {
            throw new ConflictingEventException(transactionCommand.eventId());
        }
        return new ProcessTransactionResult(
                FraudAssessmentMapper.toFraudAssessment(existingAssessment),
                false
        );
    }

    private ProcessTransactionResult assessNewTransaction(ProcessTransactionCommand transactionCommand) {
        final Instant evaluatedAt = clock.instant();
        final TransactionEvent transactionEvent = toTransactionEvent(transactionCommand);
        final FraudContext fraudContext = loadFraudContext(transactionEvent);
        final List<FraudRuleResult> matchedRules = ruleEngine.evaluate(transactionEvent, fraudContext);
        final RiskDecision decision = riskScoringService.score(matchedRules);
        final TransactionEventEntity eventEntity = toTransactionEventEntity(transactionEvent, evaluatedAt);
        final TransactionEventEntity transactionEntity = transactionRepository.save(eventEntity);

        final FraudAssessmentEntity assessmentEntity = FraudAssessmentEntity.create(
                transactionEntity,
                decision.riskScore(),
                decision.riskLevel(),
                decision.flagged(),
                evaluatedAt);

        matchedRules.forEach(rule -> assessmentEntity.addRuleResult(
                rule.ruleCode(),
                rule.score(),
                rule.reason()));

        assessmentRepository.save(assessmentEntity);

        final FraudAssessment assessment = FraudAssessmentMapper.toFraudAssessment(assessmentEntity);
        return new ProcessTransactionResult(assessment, true);
    }

    private FraudContext loadFraudContext(TransactionEvent transactionEvent) {
        final FraudProperties.Velocity velocity = fraudProperties.getRules().getVelocity();
        if (!velocity.isEnabled()) {
            return new FraudContext(0);
        }

        final Duration window = velocity.getWindow();
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("Fraud velocity window must be positive");
        }

        final LocalDateTime fromInclusive = transactionEvent.transactionTime().minus(window);
        final long recentCount = transactionRepository.countByCustomerIdAndTransactionTimeBetween(
                transactionEvent.customerId(),
                fromInclusive,
                transactionEvent.transactionTime()
        );
        return new FraudContext((int) Math.min(recentCount, Integer.MAX_VALUE));
    }

    private boolean payloadMatches(
            TransactionEventEntity existingEvent,
            ProcessTransactionCommand incomingCommand) {
        return Objects.equals(existingEvent.getTransactionId(), incomingCommand.transactionId())
                && Objects.equals(existingEvent.getCustomerId(), incomingCommand.customerId())
                && amountsAreEqual(existingEvent.getAmount(), incomingCommand.amount())
                && Objects.equals(existingEvent.getCurrency(), incomingCommand.currency())
                && Objects.equals(existingEvent.getCategory(), incomingCommand.category())
                && Objects.equals(
                        existingEvent.getTransactionType(),
                        incomingCommand.transactionType()
                )
                && Objects.equals(existingEvent.getMerchant(), incomingCommand.merchant())
                && Objects.equals(existingEvent.getCountry(), incomingCommand.country())
                && Objects.equals(
                        existingEvent.getCustomerCountry(),
                        incomingCommand.customerCountry()
                )
                && Objects.equals(
                        existingEvent.getTransactionTime(),
                        incomingCommand.transactionTime()
                );
    }

    private boolean amountsAreEqual(BigDecimal existingAmount, BigDecimal incomingAmount) {
        return existingAmount != null
                && incomingAmount != null
                && existingAmount.compareTo(incomingAmount) == 0;
    }

    private TransactionEvent toTransactionEvent(ProcessTransactionCommand transactionCommand) {
        return new TransactionEvent(
                transactionCommand.eventId(),
                transactionCommand.transactionId(),
                transactionCommand.customerId(),
                transactionCommand.amount(),
                transactionCommand.currency(),
                transactionCommand.category(),
                transactionCommand.transactionType(),
                transactionCommand.merchant(),
                transactionCommand.country(),
                transactionCommand.customerCountry(),
                transactionCommand.transactionTime()
        );
    }

    private TransactionEventEntity toTransactionEventEntity(
            TransactionEvent transactionEvent,
            Instant receivedAt) {
        return TransactionEventEntity.create(
                transactionEvent.eventId(),
                transactionEvent.transactionId(),
                transactionEvent.customerId(),
                transactionEvent.amount(),
                transactionEvent.currency(),
                transactionEvent.category(),
                transactionEvent.transactionType(),
                transactionEvent.merchant(),
                transactionEvent.country(),
                transactionEvent.customerCountry(),
                transactionEvent.transactionTime(),
                receivedAt
        );
    }
}
