package com.odwambombo.fraudruleengine.assessment.persistence;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "fraud_assessment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_fraud_assessment_transaction_event",
                        columnNames = "transaction_event_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_fraud_assessment_risk_level_evaluated_at",
                        columnList = "risk_level, evaluated_at"
                ),
                @Index(
                        name = "idx_fraud_assessment_flagged_evaluated_at",
                        columnList = "flagged, evaluated_at"
                ),
                @Index(
                        name = "idx_fraud_assessment_evaluated_at",
                        columnList = "evaluated_at"
                )
        }
)
public class FraudAssessmentEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "transaction_event_id",
            nullable = false,
            updatable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_fraud_assessment_transaction_event")
    )
    private TransactionEventEntity transactionEvent;

    @Column(name = "risk_score", nullable = false, updatable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, updatable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(name = "flagged", nullable = false, updatable = false)
    private boolean flagged;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @OneToMany(
            mappedBy = "assessment",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("ruleCode ASC")
    private List<FraudRuleResultEntity> ruleResults = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    protected FraudAssessmentEntity() {
        // Required by JPA.
    }

    private FraudAssessmentEntity(
            UUID id,
            TransactionEventEntity transactionEvent,
            int riskScore,
            RiskLevel riskLevel,
            boolean flagged,
            Instant evaluatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.transactionEvent = Objects.requireNonNull(
                transactionEvent,
                "transactionEvent must not be null"
        );
        this.riskScore = requireNonNegative(riskScore, "riskScore");
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        this.flagged = flagged;
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    }

    public static FraudAssessmentEntity create(
            TransactionEventEntity transactionEvent,
            int riskScore,
            RiskLevel riskLevel,
            boolean flagged,
            Instant evaluatedAt) {
        return new FraudAssessmentEntity(
                UUID.randomUUID(),
                transactionEvent,
                riskScore,
                riskLevel,
                flagged,
                evaluatedAt
        );
    }

    public FraudRuleResultEntity addRuleResult(String ruleCode, int score, String reason) {
        FraudRuleResultEntity ruleResult = FraudRuleResultEntity.create(
                this,
                ruleCode,
                score,
                reason
        );
        ruleResults.add(ruleResult);
        return ruleResult;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public TransactionEventEntity getTransactionEvent() {
        return transactionEvent;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public List<FraudRuleResultEntity> getRuleResults() {
        return Collections.unmodifiableList(ruleResults);
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        newEntity = false;
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
