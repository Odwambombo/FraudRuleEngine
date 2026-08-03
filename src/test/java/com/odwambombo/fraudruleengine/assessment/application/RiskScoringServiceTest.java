package com.odwambombo.fraudruleengine.assessment.application;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskScoringServiceTest {

    private final RiskScoringService service = new RiskScoringService(new FraudProperties());

    @Test
    void sumsMatchedRulesAndBuildsDecision() {
        final RiskScoringService.RiskDecision decision = service.score(List.of(
                FraudRuleResult.matched("HIGH_VALUE", 40, "High value."),
                FraudRuleResult.notMatched("UNUSUAL_TIME"),
                FraudRuleResult.matched("FOREIGN", 25, "Foreign transaction.")
        ));

        assertEquals(65, decision.riskScore());
        assertEquals(RiskLevel.HIGH, decision.riskLevel());
        assertTrue(decision.flagged());
    }

    @Test
    void mapsEveryRiskBoundary() {
        assertEquals(RiskLevel.LOW, service.determineRiskLevel(0));
        assertEquals(RiskLevel.LOW, service.determineRiskLevel(29));
        assertEquals(RiskLevel.MEDIUM, service.determineRiskLevel(30));
        assertEquals(RiskLevel.MEDIUM, service.determineRiskLevel(59));
        assertEquals(RiskLevel.HIGH, service.determineRiskLevel(60));
        assertEquals(RiskLevel.HIGH, service.determineRiskLevel(79));
        assertEquals(RiskLevel.CRITICAL, service.determineRiskLevel(80));
    }

    @Test
    void flagsAtConfiguredInclusiveThreshold() {
        assertFalse(service.isFlagged(59));
        assertTrue(service.isFlagged(60));
    }

    @Test
    void rejectsNegativeScores() {
        assertThrows(IllegalArgumentException.class, () -> service.determineRiskLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> service.isFlagged(-1));
    }
}
