package com.odwambombo.fraudruleengine.assessment.api;

import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessmentSummary;
import com.odwambombo.fraudruleengine.assessment.domain.MatchedRule;

public final class FraudAssessmentApiMapper {

    private FraudAssessmentApiMapper() { }

    public static FraudAssessmentResponse toFraudAssessmentResponse(
            FraudAssessment fraudAssessment) {
        return new FraudAssessmentResponse(
                fraudAssessment.assessmentId(),
                fraudAssessment.eventId(),
                fraudAssessment.transactionId(),
                fraudAssessment.customerId(),
                fraudAssessment.amount(),
                fraudAssessment.currency(),
                fraudAssessment.category(),
                fraudAssessment.transactionType(),
                fraudAssessment.merchant(),
                fraudAssessment.country(),
                fraudAssessment.customerCountry(),
                fraudAssessment.riskScore(),
                fraudAssessment.riskLevel(),
                fraudAssessment.flagged(),
                fraudAssessment.matchedRules().stream()
                        .map(FraudAssessmentApiMapper::toMatchedRuleResponse)
                        .toList(),
                fraudAssessment.transactionTime(),
                fraudAssessment.evaluatedAt());
    }

    public static FraudAssessmentSummaryResponse toFraudAssessmentSummaryResponse(
            FraudAssessmentSummary assessmentSummary) {
        return new FraudAssessmentSummaryResponse(
                assessmentSummary.assessmentId(),
                assessmentSummary.transactionId(),
                assessmentSummary.customerId(),
                assessmentSummary.riskScore(),
                assessmentSummary.riskLevel(),
                assessmentSummary.flagged(),
                assessmentSummary.evaluatedAt());
    }

    private static MatchedRuleResponse toMatchedRuleResponse(MatchedRule matchedRule) {
        return new MatchedRuleResponse(
                matchedRule.ruleCode(),
                matchedRule.score(),
                matchedRule.reason()
        );
    }
}
