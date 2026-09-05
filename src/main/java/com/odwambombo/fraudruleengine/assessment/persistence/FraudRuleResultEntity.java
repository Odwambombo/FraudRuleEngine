package com.odwambombo.fraudruleengine.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "fraud_rule_result",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_fraud_rule_result_assessment_rule",
                        columnNames = {"fraud_assessment_id", "rule_code"}
                )
        }
)
public class FraudRuleResultEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "fraud_assessment_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_fraud_rule_result_assessment")
    )
    private FraudAssessmentEntity assessment;

    @Column(name = "rule_code", nullable = false, updatable = false, length = 100)
    private String ruleCode;

    @Column(name = "score", nullable = false, updatable = false)
    private int score;

    @Column(name = "reason", nullable = false, updatable = false, length = 1000)
    private String reason;

    protected FraudRuleResultEntity() {
        // Required by JPA.
    }

    private FraudRuleResultEntity(
            UUID id,
            FraudAssessmentEntity assessment,
            String ruleCode,
            int score,
            String reason) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.assessment = Objects.requireNonNull(assessment, "assessment must not be null");
        this.ruleCode = Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        this.score = requireNonNegative(score);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    static FraudRuleResultEntity create(
            FraudAssessmentEntity assessment,
            String ruleCode,
            int score,
            String reason) {
        return new FraudRuleResultEntity(
                UUID.randomUUID(),
                assessment,
                ruleCode,
                score,
                reason
        );
    }

    private static int requireNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        return value;
    }
}
