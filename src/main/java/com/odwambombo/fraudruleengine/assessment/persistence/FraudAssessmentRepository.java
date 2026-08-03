package com.odwambombo.fraudruleengine.assessment.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudAssessmentRepository extends JpaRepository<FraudAssessmentEntity, UUID>, JpaSpecificationExecutor<FraudAssessmentEntity> {

    @Override
    @EntityGraph(attributePaths = "transactionEvent")
    Page<FraudAssessmentEntity> findAll(Specification<FraudAssessmentEntity> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"transactionEvent", "ruleResults"})
    @Query("""
            select distinct assessment
            from FraudAssessmentEntity assessment
            where assessment.id = :assessmentId
            """)
    Optional<FraudAssessmentEntity> findDetailedById(@Param("assessmentId") UUID assessmentId);

    @EntityGraph(attributePaths = {"transactionEvent", "ruleResults"})
    Optional<FraudAssessmentEntity> findByTransactionEvent_EventId(String eventId);

    @EntityGraph(attributePaths = "transactionEvent")
    Optional<FraudAssessmentEntity> findFirstByTransactionEvent_TransactionIdOrderByEvaluatedAtDescIdDesc(String transactionId);

    @EntityGraph(attributePaths = {"transactionEvent", "ruleResults"})
    List<FraudAssessmentEntity> findAllByTransactionEvent_TransactionIdOrderByEvaluatedAtDescIdDesc(String transactionId);
}
