package com.odwambombo.fraudruleengine.persistence;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentEntity;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentRepository;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentSpecifications;
import com.odwambombo.fraudruleengine.rule.application.FraudRuleEngine;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventEntity;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventRepository;
import com.odwambombo.fraudruleengine.transaction.application.ProcessTransactionCommand;
import com.odwambombo.fraudruleengine.transaction.application.ProcessTransactionResult;
import com.odwambombo.fraudruleengine.transaction.application.TransactionProcessingService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PostgreSqlPersistenceIntegrationTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-07-27T10:15:30Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("fraud_rule_engine")
            .withUsername("fraud")
            .withPassword("fraud");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TransactionEventRepository transactionEventRepository;

    @MockitoSpyBean
    private FraudRuleEngine fraudRuleEngine;

    @Autowired
    private FraudAssessmentRepository fraudAssessmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionProcessingService transactionProcessingService;

    @Test
    void liquibaseSchemaPersistsAndLoadsTheAssessmentAggregate() {
        Integer appliedChangeSets = jdbcTemplate.queryForObject("select count(*) from databasechangelog", Integer.class);
        assertTrue(appliedChangeSets != null && appliedChangeSets >= 3);

        TransactionEventEntity transaction = transactionEventRepository.saveAndFlush(
                event(
                        "evt-liquibase",
                        "txn-liquibase",
                        "cust-liquibase",
                        LocalDateTime.of(2026, 7, 27, 12, 15)
                )
        );
        assertFalse(transaction.isNew());

        FraudAssessmentEntity assessment = FraudAssessmentEntity.create(
                transaction,
                70,
                RiskLevel.HIGH,
                true,
                RECEIVED_AT.plusSeconds(1)
        );
        assessment.addRuleResult(
                "HIGH_VALUE_TRANSACTION",
                40,
                "Transaction amount exceeded ZAR 20000."
        );
        assessment.addRuleResult(
                "UNUSUAL_TRANSACTION_TIME",
                30,
                "Transaction occurred during the configured unusual-time window."
        );

        FraudAssessmentEntity saved = fraudAssessmentRepository.saveAndFlush(assessment);
        UUID assessmentId = saved.getId();
        assertFalse(saved.isNew());
        entityManager.clear();

        FraudAssessmentEntity reloaded = fraudAssessmentRepository
                .findDetailedById(assessmentId)
                .orElseThrow();

        assertEquals("evt-liquibase", reloaded.getTransactionEvent().getEventId());
        assertEquals("txn-liquibase", reloaded.getTransactionEvent().getTransactionId());
        assertEquals("cust-liquibase", reloaded.getTransactionEvent().getCustomerId());
        assertEquals(
                0,
                new BigDecimal("25000.00")
                        .compareTo(reloaded.getTransactionEvent().getAmount())
        );
        assertEquals("ZAR", reloaded.getTransactionEvent().getCurrency());
        assertEquals("ELECTRONICS", reloaded.getTransactionEvent().getCategory());
        assertEquals("CARD_PURCHASE", reloaded.getTransactionEvent().getTransactionType());
        assertEquals("Tech World", reloaded.getTransactionEvent().getMerchant());
        assertEquals("ZA", reloaded.getTransactionEvent().getCountry());
        assertEquals("ZA", reloaded.getTransactionEvent().getCustomerCountry());
        assertEquals(
                LocalDateTime.of(2026, 7, 27, 12, 15),
                reloaded.getTransactionEvent().getTransactionTime()
        );
        assertEquals(70, reloaded.getRiskScore());
        assertEquals(RiskLevel.HIGH, reloaded.getRiskLevel());
        assertTrue(reloaded.isFlagged());
        assertEquals(2, reloaded.getRuleResults().size());
        assertEquals(
                List.of("HIGH_VALUE_TRANSACTION", "UNUSUAL_TRANSACTION_TIME"),
                reloaded.getRuleResults().stream()
                        .map(result -> result.getRuleCode())
                        .toList()
        );
        assertTrue(reloaded.getRuleResults().stream()
                .allMatch(result -> result.getScore() > 0 && !result.getReason().isBlank()));
    }

    @Test
    void eventIdIsUnique() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 27, 12, 30);
        transactionEventRepository.saveAndFlush(
                event("evt-duplicate", "txn-original", "cust-duplicate", transactionTime)
        );

        TransactionEventEntity duplicate = event(
                "evt-duplicate",
                "txn-replayed",
                "cust-duplicate",
                transactionTime.plusSeconds(1)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> transactionEventRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    void recentCustomerCountIncludesBothWindowBoundaries() {
        LocalDateTime fromInclusive = LocalDateTime.of(2026, 7, 27, 12, 0);
        LocalDateTime toInclusive = fromInclusive.plusMinutes(10);

        transactionEventRepository.saveAllAndFlush(List.of(
                event("evt-before", "txn-before", "cust-velocity", fromInclusive.minusNanos(1_000)),
                event("evt-from", "txn-from", "cust-velocity", fromInclusive),
                event("evt-middle", "txn-middle", "cust-velocity", fromInclusive.plusMinutes(5)),
                event("evt-to", "txn-to", "cust-velocity", toInclusive),
                event("evt-after", "txn-after", "cust-velocity", toInclusive.plusNanos(1_000)),
                event("evt-other", "txn-other", "cust-other", fromInclusive.plusMinutes(5))
        ));

        long recentCount = transactionEventRepository
                .countByCustomerIdAndTransactionTimeBetween(
                        "cust-velocity",
                        fromInclusive,
                        toInclusive
                );

        assertEquals(3, recentCount);
    }

    @Test
    void repositoryQueriesBindSqlInjectionShapedValuesAsData() {
        final LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 27, 12, 30);
        final TransactionEventEntity seededTransaction = transactionEventRepository.saveAndFlush(
                event(
                        "evt-sql-binding",
                        "txn-sql-binding",
                        "cust-sql-binding",
                        transactionTime
                )
        );
        fraudAssessmentRepository.saveAndFlush(FraudAssessmentEntity.create(
                seededTransaction,
                0,
                RiskLevel.LOW,
                false,
                RECEIVED_AT
        ));
        entityManager.clear();

        final String sqlInjectionShapedValue = "' OR '1'='1' --";

        assertTrue(fraudAssessmentRepository
                .findFirstByTransactionEvent_TransactionIdOrderByEvaluatedAtDescIdDesc(
                        sqlInjectionShapedValue
                )
                .isEmpty());
        assertTrue(fraudAssessmentRepository.findAll(
                FraudAssessmentSpecifications.withFilters(
                        sqlInjectionShapedValue,
                        null,
                        null,
                        null,
                        null
                ),
                Pageable.unpaged()
        ).isEmpty());
        assertEquals(
                0L,
                transactionEventRepository.countByCustomerIdAndTransactionTimeBetween(
                        sqlInjectionShapedValue,
                        transactionTime.minusMinutes(10),
                        transactionTime.plusMinutes(10)
                )
        );
        assertEquals(1L, transactionEventRepository.count());
        assertEquals(1L, fraudAssessmentRepository.count());

        final TransactionEventEntity subsequentTransaction = transactionEventRepository
                .saveAndFlush(event(
                        "evt-after-sql-binding",
                        "txn-after-sql-binding",
                        "cust-after-sql-binding",
                        transactionTime.plusMinutes(1)
                ));
        fraudAssessmentRepository.saveAndFlush(FraudAssessmentEntity.create(
                subsequentTransaction,
                0,
                RiskLevel.LOW,
                false,
                RECEIVED_AT.plusSeconds(1)
        ));

        assertEquals(2L, transactionEventRepository.count());
        assertEquals(2L, fraudAssessmentRepository.count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serializableRetryPreservesVelocityDuringConcurrentProcessing() throws Exception {
        fraudAssessmentRepository.deleteAll();
        transactionEventRepository.deleteAll();
        final LocalDateTime baseTime = LocalDateTime.of(2026, 7, 27, 12, 0);
        for (int index = 1; index <= 3; index++) {
            transactionProcessingService.processTransaction(command(
                    "evt-velocity-seed-" + index,
                    "txn-velocity-seed-" + index,
                    baseTime.minusMinutes(6 - index)
            ));
        }

        final CyclicBarrier initialVelocityReads = new CyclicBarrier(2);
        final AtomicInteger ruleEvaluationCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (ruleEvaluationCalls.incrementAndGet() <= 2) {
                initialVelocityReads.await(5, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(fraudRuleEngine).evaluate(
                any(TransactionEvent.class),
                any(FraudContext.class)
        );

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<ProcessTransactionResult>> futures = List.of(
                    executor.submit(() -> concurrentProcess(
                            command("evt-velocity-four", "txn-velocity-four", baseTime),
                            ready,
                            start
                    )),
                    executor.submit(() -> concurrentProcess(
                            command(
                                    "evt-velocity-five",
                                    "txn-velocity-five",
                                    baseTime
                            ),
                            ready,
                            start
                    ))
            );
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            final List<Integer> scores = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS).assessment().riskScore(),
                    futures.get(1).get(10, TimeUnit.SECONDS).assessment().riskScore()
            ).stream().sorted().toList();

            assertEquals(List.of(0, 35), scores);
            assertEquals(5L, transactionEventRepository.count());
            assertTrue(ruleEvaluationCalls.get() >= 3);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            fraudAssessmentRepository.deleteAll();
            transactionEventRepository.deleteAll();
        }
    }

    private ProcessTransactionResult concurrentProcess(
            ProcessTransactionCommand command,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent processing start timed out");
        }
        return transactionProcessingService.processTransaction(command);
    }

    private ProcessTransactionCommand command(
            String eventId,
            String transactionId,
            LocalDateTime transactionTime) {
        return new ProcessTransactionCommand(
                eventId,
                transactionId,
                "cust-concurrent-velocity",
                new BigDecimal("100.00"),
                "ZAR",
                "GROCERIES",
                "CARD_PURCHASE",
                "Grocery Store",
                "ZA",
                "ZA",
                transactionTime
        );
    }

    private TransactionEventEntity event(
            String eventId,
            String transactionId,
            String customerId,
            LocalDateTime transactionTime) {
        return TransactionEventEntity.create(
                eventId,
                transactionId,
                customerId,
                new BigDecimal("25000.00"),
                "ZAR",
                "ELECTRONICS",
                "CARD_PURCHASE",
                "Tech World",
                "ZA",
                "ZA",
                transactionTime,
                RECEIVED_AT
        );
    }
}
