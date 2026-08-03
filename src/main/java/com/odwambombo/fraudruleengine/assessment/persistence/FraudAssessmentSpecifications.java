package com.odwambombo.fraudruleengine.assessment.persistence;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class FraudAssessmentSpecifications {

    private FraudAssessmentSpecifications() {
    }

    public static Specification<FraudAssessmentEntity> withFilters(
            String customerId,
            RiskLevel riskLevel,
            Boolean flagged,
            Instant fromInclusive,
            Instant toInclusive) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (customerId != null && !customerId.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        root.join("transactionEvent", JoinType.INNER).get("customerId"),
                        customerId
                ));
            }

            if (riskLevel != null) {
                predicates.add(criteriaBuilder.equal(root.get("riskLevel"), riskLevel));
            }

            if (flagged != null) {
                predicates.add(criteriaBuilder.equal(root.get("flagged"), flagged));
            }

            Path<Instant> evaluatedAt = root.get("evaluatedAt");
            if (fromInclusive != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        evaluatedAt,
                        fromInclusive
                ));
            }

            if (toInclusive != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        evaluatedAt,
                        toInclusive
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
