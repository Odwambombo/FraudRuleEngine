package com.odwambombo.fraudruleengine.assessment.application;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;

@Service
public class RiskScoringService {

    private final FraudProperties fraudProperties;

    public RiskScoringService(FraudProperties fraudProperties) {
        this.fraudProperties = fraudProperties;
    }

    public RiskDecision score(Collection<FraudRuleResult> ruleResults) {
        final int riskScore = calculateRiskScore(ruleResults);
        return new RiskDecision(
                riskScore,
                determineRiskLevel(riskScore),
                isFlagged(riskScore)
        );
    }

    public int calculateRiskScore(Collection<FraudRuleResult> ruleResults) {
        Objects.requireNonNull(ruleResults, "ruleResults must not be null");

        return ruleResults.stream()
                .filter(Objects::nonNull)
                .filter(FraudRuleResult::matched)
                .mapToInt(FraudRuleResult::score)
                .reduce(0, Math::addExact);
    }

    public RiskLevel determineRiskLevel(int riskScore) {
        requireNonNegativeRiskScore(riskScore);
        final FraudProperties.Risk risk = getValidatedRiskConfiguration();

        if (riskScore >= risk.getCriticalThreshold()) {
            return RiskLevel.CRITICAL;
        }
        if (riskScore >= risk.getHighThreshold()) {
            return RiskLevel.HIGH;
        }
        if (riskScore >= risk.getMediumThreshold()) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    public boolean isFlagged(int riskScore) {
        requireNonNegativeRiskScore(riskScore);
        final FraudProperties.Risk risk = getValidatedRiskConfiguration();
        return riskScore >= risk.getFlagThreshold();
    }

    private FraudProperties.Risk getValidatedRiskConfiguration() {
        final FraudProperties.Risk risk = fraudProperties.getRisk();
        if (risk.getMediumThreshold() < 0
                || risk.getMediumThreshold() >= risk.getHighThreshold()
                || risk.getHighThreshold() >= risk.getCriticalThreshold()
                || risk.getFlagThreshold() < 0) {
            throw new IllegalStateException("Fraud risk thresholds are invalid");
        }
        return risk;
    }

    private void requireNonNegativeRiskScore(int riskScore) {
        if (riskScore < 0) {
            throw new IllegalArgumentException("riskScore must not be negative");
        }
    }

    public record RiskDecision(int riskScore, RiskLevel riskLevel, boolean flagged) {
    }
}
