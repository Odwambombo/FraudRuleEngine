package com.odwambombo.fraudruleengine.assessment.application;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;

import java.time.Instant;

public record FraudAssessmentQuery(
        String customerId,
        RiskLevel riskLevel,
        Boolean flagged,
        Instant from,
        Instant to,
        int page,
        int size) {
}
