package com.odwambombo.fraudruleengine.api;

import com.jayway.jsonpath.JsonPath;
import com.odwambombo.fraudruleengine.assessment.persistence.FraudAssessmentRepository;
import com.odwambombo.fraudruleengine.shared.validation.ApiIdentifier;
import com.odwambombo.fraudruleengine.transaction.persistence.TransactionEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:fraud_rule_engine_api;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "fraud.security.enabled=false"
})
@AutoConfigureMockMvc
@Import(FraudRuleEngineApiTest.TestClockConfiguration.class)
class FraudRuleEngineApiTest {

    private static final Instant INITIAL_INSTANT = Instant.parse("2026-07-27T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionEventRepository transactionRepository;

    @Autowired
    private FraudAssessmentRepository assessmentRepository;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetDatabaseAndClock() {
        assessmentRepository.deleteAll();
        transactionRepository.deleteAll();
        clock.set(INITIAL_INSTANT);
    }

    @Test
    void postCreatesHighRiskAssessmentAndSetsLocation() throws Exception {
        final MvcResult result = mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction(
                                "evt-10001",
                                "txn-50001",
                                "cust-123",
                                "25000.00",
                                "ELECTRONICS",
                                "2026-07-27T02:15:00"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-10001"))
                .andExpect(jsonPath("$.transactionId").value("txn-50001"))
                .andExpect(jsonPath("$.customerId").value("cust-123"))
                .andExpect(jsonPath("$.amount").value(25000.00))
                .andExpect(jsonPath("$.currency").value("ZAR"))
                .andExpect(jsonPath("$.category").value("ELECTRONICS"))
                .andExpect(jsonPath("$.transactionType").value("CARD_PURCHASE"))
                .andExpect(jsonPath("$.merchant").value("Tech World"))
                .andExpect(jsonPath("$.country").value("ZA"))
                .andExpect(jsonPath("$.transactionTime").value("2026-07-27T02:15:00"))
                .andExpect(jsonPath("$.riskScore").value(60))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.flagged").value(true))
                .andExpect(jsonPath("$.matchedRules.length()").value(2))
                .andExpect(jsonPath("$.matchedRules[0].ruleCode")
                        .value("HIGH_VALUE_TRANSACTION"))
                .andExpect(jsonPath("$.matchedRules[0].score").value(40))
                .andExpect(jsonPath("$.matchedRules[1].ruleCode")
                        .value("UNUSUAL_TRANSACTION_TIME"))
                .andExpect(jsonPath("$.matchedRules[1].score").value(20))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();

        final String assessmentId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.assessmentId"
        );
        assertEquals(
                "/api/v1/fraud-assessments/" + assessmentId,
                result.getResponse().getHeader(HttpHeaders.LOCATION)
        );
    }

    @Test
    void duplicateEventReturnsExistingAssessmentWithoutPersistingAgain() throws Exception {
        final String payload = sampleTransaction(
                "evt-duplicate",
                "txn-duplicate",
                "cust-duplicate",
                "25000.00",
                "ELECTRONICS",
                "2026-07-27T02:15:00"
        );
        final MvcResult created = mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        final String assessmentId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.assessmentId"
        );

        final MvcResult replayed = mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value(assessmentId))
                .andExpect(jsonPath("$.eventId").value("evt-duplicate"))
                .andReturn();

        assertNull(replayed.getResponse().getHeader(HttpHeaders.LOCATION));
        assertEquals(1L, transactionRepository.count());
        assertEquals(1L, assessmentRepository.count());
    }

    @Test
    void concurrentDuplicateRequestsReturnOneCreationAndOneReplay() throws Exception {
        final String payload = sampleTransaction(
                "evt-concurrent",
                "txn-concurrent",
                "cust-concurrent",
                "25000.00",
                "ELECTRONICS",
                "2026-07-27T02:15:00");
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            final List<Future<MvcResult>> requests = List.of(
                    executor.submit(() -> concurrentPost(payload, ready, start)),
                    executor.submit(() -> concurrentPost(payload, ready, start))
            );
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            final MvcResult first = requests.get(0).get(10, TimeUnit.SECONDS);
            final MvcResult second = requests.get(1).get(10, TimeUnit.SECONDS);
            final List<Integer> statuses = List.of(
                    first.getResponse().getStatus(),
                    second.getResponse().getStatus()
            ).stream().sorted().toList();
            final String firstAssessmentId = JsonPath.read(
                    first.getResponse().getContentAsString(),
                    "$.assessmentId"
            );
            final String secondAssessmentId = JsonPath.read(
                    second.getResponse().getContentAsString(),
                    "$.assessmentId"
            );

            assertEquals(List.of(200, 201), statuses);
            assertEquals(firstAssessmentId, secondAssessmentId);
            assertEquals(1L, transactionRepository.count());
            assertEquals(1L, assessmentRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reusingEventIdWithAnyChangedTransactionFieldReturnsConflict() throws Exception {
        final String original = """
                {
                  "eventId": "evt-conflict",
                  "transactionId": "txn-original",
                  "customerId": "cust-original",
                  "amount": 100.00,
                  "currency": "ZAR",
                  "category": "GROCERIES",
                  "transactionType": "CARD_PURCHASE",
                  "merchant": "Grocery Store",
                  "country": "ZA",
                  "customerCountry": "ZA",
                  "transactionTime": "2026-07-27T12:00:00"
                }
                """;
        createAssessment(original);

        final List<String> changedPayloads = List.of(
                original.replace("\"txn-original\"", "\"txn-changed\""),
                original.replace("\"cust-original\"", "\"cust-changed\""),
                original.replace("\"amount\": 100.00", "\"amount\": 101.00"),
                original.replace("\"currency\": \"ZAR\"", "\"currency\": \"USD\""),
                original.replace("\"category\": \"GROCERIES\"", "\"category\": \"FUEL\""),
                original.replace("\"transactionType\": \"CARD_PURCHASE\"", "\"transactionType\": \"TRANSFER\""),
                original.replace("\"merchant\": \"Grocery Store\"", "\"merchant\": \"Other Store\""),
                original.replace("\"country\": \"ZA\",", "\"country\": \"US\","),
                original.replace("\"customerCountry\": \"ZA\"", "\"customerCountry\": \"US\""),
                original.replace("\"transactionTime\": \"2026-07-27T12:00:00\"", "\"transactionTime\": \"2026-07-27T12:00:01\"")
        );

        for (String changedPayload : changedPayloads) {
            mockMvc.perform(post("/api/v1/transaction-events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changedPayload))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVENT_ID_CONFLICT"))
                    .andExpect(jsonPath("$.message").value(
                            "Event ID 'evt-conflict' was already used for a different transaction payload."
                    ));
        }

        assertEquals(1L, transactionRepository.count());
        assertEquals(1L, assessmentRepository.count());
    }

    @Test
    void replayComparisonUsesCanonicalStoredValues() throws Exception {
        final String original = """
                {
                  "eventId": "evt-canonical",
                  "transactionId": "txn-canonical",
                  "customerId": "cust-canonical",
                  "amount": 100,
                  "currency": "zar",
                  "category": "groceries",
                  "transactionType": "card_purchase",
                  "merchant": "  Grocery Store  ",
                  "country": "za",
                  "customerCountry": "za",
                  "transactionTime": "2026-07-27T12:00:00.123456789"
                }
                """;
        createAssessment(original);

        mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-canonical",
                                  "transactionId": "txn-canonical",
                                  "customerId": "cust-canonical",
                                  "amount": 100.00,
                                  "currency": "ZAR",
                                  "category": "GROCERIES",
                                  "transactionType": "CARD_PURCHASE",
                                  "merchant": "Grocery Store",
                                  "country": "ZA",
                                  "customerCountry": "ZA",
                                  "transactionTime": "2026-07-27T12:00:00.123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").exists());

        assertEquals(1L, transactionRepository.count());
        assertEquals(1L, assessmentRepository.count());
    }

    @Test
    void fifthTransactionWithinTenMinutesMatchesVelocityRule() throws Exception {
        for (int index = 1; index <= 4; index++) {
            createAssessment(sampleTransaction(
                    "evt-velocity-" + index,
                    "txn-velocity-" + index,
                    "cust-velocity",
                    "100.00",
                    "GROCERIES",
                    "2026-07-27T11:%02d:00".formatted(49 + (index * 2))
            ));
        }

        mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleTransaction(
                                "evt-velocity-5",
                                "txn-velocity-5",
                                "cust-velocity",
                                "100.00",
                                "GROCERIES",
                                "2026-07-27T12:00:00"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskScore").value(35))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.flagged").value(false))
                .andExpect(jsonPath("$.matchedRules.length()").value(1))
                .andExpect(jsonPath("$.matchedRules[0].ruleCode")
                        .value("TRANSACTION_VELOCITY"));
    }

    @Test
    void customerCountryEnablesExplainableForeignAndRiskyCategoryMatches() throws Exception {
        final String payload = sampleTransaction(
                "evt-context",
                "txn-context",
                "cust-context",
                "100.00",
                "GAMBLING",
                "2026-07-27T12:00:00"
        ).replace(
                "\"country\": \"ZA\",",
                "\"country\": \"US\", \"customerCountry\": \"ZA\","
        );

        mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.customerCountry").value("ZA"))
                .andExpect(jsonPath("$.riskScore").value(40))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.flagged").value(false))
                .andExpect(jsonPath("$.matchedRules.length()").value(2))
                .andExpect(jsonPath("$.matchedRules[0].ruleCode")
                        .value("FOREIGN_TRANSACTION"))
                .andExpect(jsonPath("$.matchedRules[1].ruleCode")
                        .value("RISKY_TRANSACTION_CATEGORY"));
    }

    @Test
    void invalidPayloadReturnsStructuredFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": " ",
                                  "transactionId": "txn/invalid",
                                  "customerId": "cust-invalid",
                                  "amount": 0,
                                  "currency": "ZA",
                                  "category": "ELECTRONICS",
                                  "transactionType": "CARD_PURCHASE",
                                  "merchant": "Tech World",
                                  "country": "ZA",
                                  "transactionTime": "2026-07-27T02:15:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"))
                .andExpect(jsonPath("$.message").value("The transaction event is invalid."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.eventId").exists())
                .andExpect(jsonPath("$.errors.transactionId").value(ApiIdentifier.DEFAULT_MESSAGE))
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists());

        assertEquals(0L, transactionRepository.count());
        assertEquals(0L, assessmentRepository.count());
    }

    @Test
    void retrievalIdentifiersUseTheSameApiValidationContract() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/fraud-assessments/transaction/{transactionId}",
                        "invalid$transaction"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("The request contains an invalid value."));

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("customerId", "invalid customer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("The request contains an invalid value."));
    }

    @Test
    void sqlInjectionShapedIdentifierInputsAreRejectedBeforeQueryExecution() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-assessments").queryParam("customerId", "cust-safe' OR '1'='1'--"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request contains an invalid value."))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get(
                        "/api/v1/fraud-assessments/transaction/{transactionId}",
                        "txn-safe' OR '1'='1'--"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request contains an invalid value."))
                .andExpect(jsonPath("$.timestamp").exists());

        assertEquals(0L, transactionRepository.count());
        assertEquals(0L, assessmentRepository.count());
    }

    @Test
    void sqlLookingMerchantIsPersistedAsDataAndLeavesTheSchemaUsable() throws Exception {
        final String merchant = "O'Malley's Market'); DROP TABLE transaction_event; --";
        final String payload = sampleTransaction(
                "evt-merchant-text",
                "txn-merchant-text",
                "cust-merchant-text",
                "100.00",
                "GROCERIES",
                "2026-07-27T12:00:00").replace("Tech World", merchant);

        final MvcResult created = mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchant").value(merchant))
                .andReturn();
        final String assessmentId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.assessmentId"
        );

        mockMvc.perform(get("/api/v1/fraud-assessments/{assessmentId}", assessmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value(merchant));

        assertEquals(
                merchant,
                transactionRepository
                        .findAllByTransactionIdOrderByReceivedAtDesc("txn-merchant-text")
                        .getFirst()
                        .getMerchant()
        );

        createAssessment(sampleTransaction(
                "evt-after-merchant-text",
                "txn-after-merchant-text",
                "cust-after-merchant-text",
                "100.00",
                "GROCERIES",
                "2026-07-27T12:01:00"
        ));
        assertEquals(2L, transactionRepository.count());
        assertEquals(2L, assessmentRepository.count());
    }

    @Test
    void getByIdAndTransactionIdReturnTheSavedAssessment() throws Exception {
        final MvcResult created = createAssessment(
                sampleTransaction(
                        "evt-retrieve",
                        "txn-retrieve",
                        "cust-retrieve",
                        "25000.00",
                        "ELECTRONICS",
                        "2026-07-27T02:15:00"
                )
        );
        final String assessmentId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.assessmentId"
        );

        mockMvc.perform(get("/api/v1/fraud-assessments/{assessmentId}", assessmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value(assessmentId))
                .andExpect(jsonPath("$.eventId").value("evt-retrieve"))
                .andExpect(jsonPath("$.transactionId").value("txn-retrieve"))
                .andExpect(jsonPath("$.amount").value(25000.00))
                .andExpect(jsonPath("$.currency").value("ZAR"))
                .andExpect(jsonPath("$.category").value("ELECTRONICS"))
                .andExpect(jsonPath("$.transactionType").value("CARD_PURCHASE"))
                .andExpect(jsonPath("$.merchant").value("Tech World"))
                .andExpect(jsonPath("$.country").value("ZA"))
                .andExpect(jsonPath("$.transactionTime").value("2026-07-27T02:15:00"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.matchedRules[0].score").value(40))
                .andExpect(jsonPath("$.matchedRules[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.matchedRules[1].score").value(20))
                .andExpect(jsonPath("$.matchedRules[1].reason").isNotEmpty());

        mockMvc.perform(get("/api/v1/fraud-assessments/transaction/{transactionId}", "txn-retrieve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value(assessmentId))
                .andExpect(jsonPath("$.eventId").value("evt-retrieve"))
                .andExpect(jsonPath("$.transactionId").value("txn-retrieve"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"));
    }

    @Test
    void safeTransactionPersistsLowDecisionWithNoRuleMatches() throws Exception {
        final MvcResult created = createAssessment(sampleTransaction(
                "evt-safe",
                "txn-safe",
                "cust-safe",
                "100.00",
                "GROCERIES",
                "2026-07-27T12:00:00"));
        final String assessmentId = JsonPath.read(created.getResponse().getContentAsString(), "$.assessmentId");

        mockMvc.perform(get("/api/v1/fraud-assessments/{assessmentId}", assessmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskScore").value(0))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.flagged").value(false))
                .andExpect(jsonPath("$.matchedRules.length()").value(0));
    }

    @Test
    void missingAssessmentsReturnStructuredNotFoundResponses() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-assessments/{assessmentId}", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Fraud assessment '00000000-0000-0000-0000-000000000001' was not found."))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/fraud-assessments/transaction/{transactionId}", "txn-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No fraud assessment was found for transaction 'txn-missing'."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void collectionSupportsFiltersPaginationAndNewestFirstOrdering() throws Exception {
        createAssessment(sampleTransaction(
                "evt-low",
                "txn-low",
                "cust-a",
                "100.00",
                "GROCERIES",
                "2026-07-27T12:00:00"));
        clock.advance(Duration.ofMinutes(1));

        createAssessment(sampleTransaction(
                "evt-medium",
                "txn-medium",
                "cust-b",
                "25000.00",
                "ELECTRONICS",
                "2026-07-27T12:00:00"));
        clock.advance(Duration.ofMinutes(1));

        createAssessment(sampleTransaction(
                "evt-high",
                "txn-high",
                "cust-a",
                "25000.00",
                "ELECTRONICS",
                "2026-07-27T02:15:00"));

        mockMvc.perform(get("/api/v1/fraud-assessments").queryParam("customerId", "cust-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-high"))
                .andExpect(jsonPath("$.content[1].transactionId").value("txn-low"));

        mockMvc.perform(get("/api/v1/fraud-assessments").queryParam("riskLevel", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-medium"))
                .andExpect(jsonPath("$.content[0].riskLevel").value("MEDIUM"));

        mockMvc.perform(get("/api/v1/fraud-assessments").queryParam("flagged", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-high"))
                .andExpect(jsonPath("$.content[0].flagged").value(true));

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("from", "2026-07-27T10:01:00Z")
                        .queryParam("to", "2026-07-27T10:02:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-high"))
                .andExpect(jsonPath("$.content[1].transactionId").value("txn-medium"));

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("from", "2026-07-27")
                        .queryParam("to", "2026-07-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-high"))
                .andExpect(jsonPath("$.content[1].transactionId").value("txn-medium"));

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-low"));
    }

    @Test
    void collectionRejectsInvalidSizeAndDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100."))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("from", "2026-07-28T00:00:00Z")
                        .queryParam("to", "2026-07-27T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "from must be earlier than or equal to to."
                ))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/fraud-assessments")
                        .queryParam("from", "27-07-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "from must be an ISO-8601 date or offset date-time."
                ));
    }

    private MvcResult createAssessment(String payload) throws Exception {
        return mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult concurrentPost(
            String payload,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request start timed out");
        }
        return mockMvc.perform(post("/api/v1/transaction-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();
    }

    private String sampleTransaction(
            String eventId,
            String transactionId,
            String customerId,
            String amount,
            String category,
            String transactionTime) {
        return """
                {
                  "eventId": "%s",
                  "transactionId": "%s",
                  "customerId": "%s",
                  "amount": %s,
                  "currency": "ZAR",
                  "category": "%s",
                  "transactionType": "CARD_PURCHASE",
                  "merchant": "Tech World",
                  "country": "ZA",
                  "transactionTime": "%s"
                }
                """.formatted(
                eventId,
                transactionId,
                customerId,
                amount,
                category,
                transactionTime
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(INITIAL_INSTANT);
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        private MutableClock(Instant initialInstant) {
            current = new AtomicReference<>(initialInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        void set(Instant instant) {
            current.set(instant);
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }
    }
}
