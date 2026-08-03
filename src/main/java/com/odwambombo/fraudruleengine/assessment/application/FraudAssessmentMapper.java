package com.odwambombo.fraudruleengine.assessment.application;

import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessmentSummary;
import com.odwambombo.fraudruleengine.assessment.domain.MatchedRule;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentEntity;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudRuleResultEntity;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventEntity;

import java.util.Comparator;

public final class FraudAssessmentMapper {

    private static final Comparator<FraudRuleResultEntity> RULE_ORDER =
            Comparator.comparing(FraudRuleResultEntity::getRuleCode);

    private FraudAssessmentMapper() {
    }

    public static FraudAssessment toFraudAssessment(FraudAssessmentEntity assessmentEntity) {
        final TransactionEventEntity transactionEvent = assessmentEntity.getTransactionEvent();
        return new FraudAssessment(
                assessmentEntity.getId(),
                transactionEvent.getEventId(),
                transactionEvent.getTransactionId(),
                transactionEvent.getCustomerId(),
                transactionEvent.getAmount(),
                transactionEvent.getCurrency(),
                transactionEvent.getCategory(),
                transactionEvent.getTransactionType(),
                transactionEvent.getMerchant(),
                transactionEvent.getCountry(),
                transactionEvent.getCustomerCountry(),
                assessmentEntity.getRiskScore(),
                assessmentEntity.getRiskLevel(),
                assessmentEntity.isFlagged(),
                assessmentEntity.getRuleResults().stream()
                        .sorted(RULE_ORDER)
                        .map(ruleResultEntity -> new MatchedRule(
                                ruleResultEntity.getRuleCode(),
                                ruleResultEntity.getScore(),
                                ruleResultEntity.getReason()
                        ))
                        .toList(),
                transactionEvent.getTransactionTime(),
                assessmentEntity.getEvaluatedAt()
        );
    }

    public static FraudAssessmentSummary toFraudAssessmentSummary(
            FraudAssessmentEntity assessmentEntity) {
        final TransactionEventEntity transactionEvent = assessmentEntity.getTransactionEvent();
        return new FraudAssessmentSummary(
                assessmentEntity.getId(),
                transactionEvent.getTransactionId(),
                transactionEvent.getCustomerId(),
                assessmentEntity.getRiskScore(),
                assessmentEntity.getRiskLevel(),
                assessmentEntity.isFlagged(),
                assessmentEntity.getEvaluatedAt()
        );
    }
}
