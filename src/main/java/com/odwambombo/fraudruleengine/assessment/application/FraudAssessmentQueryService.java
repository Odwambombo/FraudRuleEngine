package com.odwambombo.fraudruleengine.assessment.application;

import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessmentSummary;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentEntity;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentRepository;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentSpecifications;
import com.odwambombo.fraudruleengine.shared.exception.InvalidRequestException;
import com.odwambombo.fraudruleengine.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FraudAssessmentQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("evaluatedAt"),
            Sort.Order.desc("id")
    );

    private final FraudAssessmentRepository assessmentRepository;

    public FraudAssessmentQueryService(FraudAssessmentRepository assessmentRepository) {
        this.assessmentRepository = assessmentRepository;
    }

    public FraudAssessment getById(UUID assessmentId) {
        return assessmentRepository.findDetailedById(assessmentId)
                .map(FraudAssessmentMapper::toFraudAssessment)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Fraud assessment '%s' was not found.".formatted(assessmentId)
                ));
    }

    public FraudAssessment getLatestByTransactionId(String transactionId) {
        return assessmentRepository
                .findFirstByTransactionEvent_TransactionIdOrderByEvaluatedAtDescIdDesc(
                        transactionId
                )
                .map(FraudAssessmentMapper::toFraudAssessment)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No fraud assessment was found for transaction '%s'."
                                .formatted(transactionId)
                ));
    }

    public Page<FraudAssessmentSummary> search(FraudAssessmentQuery assessmentQuery) {
        validateQuery(assessmentQuery);
        final PageRequest pageRequest = PageRequest.of(
                assessmentQuery.page(),
                assessmentQuery.size(),
                DEFAULT_SORT
        );
        final Page<FraudAssessmentEntity> assessments = assessmentRepository.findAll(
                FraudAssessmentSpecifications.withFilters(
                        normalizeCustomerId(assessmentQuery.customerId()),
                        assessmentQuery.riskLevel(),
                        assessmentQuery.flagged(),
                        assessmentQuery.from(),
                        assessmentQuery.to()
                ),
                pageRequest
        );
        return assessments.map(FraudAssessmentMapper::toFraudAssessmentSummary);
    }

    private void validateQuery(FraudAssessmentQuery assessmentQuery) {
        if (assessmentQuery.page() < 0) {
            throw new InvalidRequestException("page must be zero or greater.");
        }
        if (assessmentQuery.size() < 1 || assessmentQuery.size() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and 100.");
        }
        if (assessmentQuery.from() != null
                && assessmentQuery.to() != null
                && assessmentQuery.from().isAfter(assessmentQuery.to())) {
            throw new InvalidRequestException("from must be earlier than or equal to to.");
        }
    }

    private String normalizeCustomerId(String customerId) {
        return customerId == null || customerId.isBlank() ? null : customerId.trim();
    }

}
