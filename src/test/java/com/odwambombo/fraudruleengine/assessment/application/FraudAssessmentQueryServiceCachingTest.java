package com.odwambombo.fraudruleengine.assessment.application;

import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentEntity;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentRepository;
import com.odwambombo.fraudruleengine.shared.exception.ResourceNotFoundException;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(FraudAssessmentQueryServiceCachingTest.TestConfiguration.class)
class FraudAssessmentQueryServiceCachingTest {

    private static final String ASSESSMENT_CACHE = "fraudAssessmentsById";

    @Autowired
    private FraudAssessmentQueryService queryService;

    @Autowired
    private FraudAssessmentRepository assessmentRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetRepositoryAndCache() {
        reset(assessmentRepository);
        cacheManager.getCache(ASSESSMENT_CACHE).clear();
    }

    @Test
    void repeatedAssessmentLookupUsesTheCachedImmutableResult() {
        final FraudAssessmentEntity entity = assessmentEntity();
        final UUID assessmentId = entity.getId();
        when(assessmentRepository.findDetailedById(assessmentId))
                .thenReturn(Optional.of(entity));

        final FraudAssessment firstResult = queryService.getById(assessmentId);
        final FraudAssessment secondResult = queryService.getById(assessmentId);

        assertThat(secondResult).isSameAs(firstResult);
        verify(assessmentRepository, times(1)).findDetailedById(assessmentId);
    }

    @Test
    void missingAssessmentsAreNotCached() {
        final UUID assessmentId = UUID.randomUUID();
        when(assessmentRepository.findDetailedById(assessmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getById(assessmentId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> queryService.getById(assessmentId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assessmentRepository, times(2)).findDetailedById(assessmentId);
    }

    private FraudAssessmentEntity assessmentEntity() {
        final Instant evaluatedAt = Instant.parse("2026-09-05T08:00:00Z");
        final TransactionEventEntity transactionEvent = TransactionEventEntity.create(
                "evt-cache",
                "txn-cache",
                "cust-cache",
                new BigDecimal("100.00"),
                "ZAR",
                "GROCERIES",
                "CARD_PURCHASE",
                "Cache Test Merchant",
                "ZA",
                "ZA",
                LocalDateTime.parse("2026-09-05T10:00:00"),
                evaluatedAt
        );
        return FraudAssessmentEntity.create(
                transactionEvent,
                0,
                RiskLevel.LOW,
                false,
                evaluatedAt
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class TestConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(ASSESSMENT_CACHE);
        }

        @Bean
        FraudAssessmentRepository assessmentRepository() {
            return mock(FraudAssessmentRepository.class);
        }

        @Bean
        FraudAssessmentQueryService fraudAssessmentQueryService(
                FraudAssessmentRepository assessmentRepository) {
            return new FraudAssessmentQueryService(assessmentRepository);
        }
    }
}
