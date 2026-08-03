package com.odwambombo.fraudruleengine.assessment.api;

import com.odwambombo.fraudruleengine.assessment.application.FraudAssessmentQuery;
import com.odwambombo.fraudruleengine.assessment.application.FraudAssessmentQueryService;
import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessmentSummary;
import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;
import com.odwambombo.fraudruleengine.shared.exception.ApiErrorResponse;
import com.odwambombo.fraudruleengine.shared.exception.InvalidRequestException;
import com.odwambombo.fraudruleengine.shared.validation.ApiIdentifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud-assessments")
@Tag(
        name = "Fraud assessments",
        description = "Searches and retrieves persisted, explainable fraud decisions."
)
@SecurityRequirement(name = "bearerAuth")
public class FraudAssessmentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FraudAssessmentController.class);

    private final FraudAssessmentQueryService queryService;

    public FraudAssessmentController(FraudAssessmentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{assessmentId}")
    @Operation(
            summary = "Get a fraud assessment by ID",
            description = "Returns the complete saved decision, transaction fields, and matched-rule reasons."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The requested fraud assessment.",
                    content = @Content(schema = @Schema(implementation = FraudAssessmentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The assessment ID is not a valid UUID.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "A valid bearer token is required."),
            @ApiResponse(
                    responseCode = "403",
                    description = "ASSESSMENT_READ and a granting role are required."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No assessment exists with the supplied ID.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PreAuthorize("@fraudAuthorizationPolicy.hasPermissionAndGrantingRole(authentication, "
            + "T(com.odwambombo.fraudruleengine.shared.security.FraudPermission)"
            + ".ASSESSMENT_READ)")
    public ResponseEntity<FraudAssessmentResponse> getById(
            @Parameter(description = "Generated fraud-assessment UUID.")
            @PathVariable UUID assessmentId) {
        final FraudAssessment fraudAssessment = queryService.getById(assessmentId);
        final FraudAssessmentResponse assessmentResponse = FraudAssessmentApiMapper.toFraudAssessmentResponse(fraudAssessment);
        logSuccessfulAssessmentLookup("get_assessment_by_id", assessmentResponse.assessmentId());
        return ResponseEntity.ok(assessmentResponse);
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(
            summary = "Get the latest assessment for a transaction",
            description = "Returns the newest persisted assessment for the supplied source-system "
                    + "transaction ID, ordered deterministically by evaluation time and assessment ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The latest fraud assessment for the transaction.",
                    content = @Content(schema = @Schema(implementation = FraudAssessmentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The transaction identifier is invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "A valid bearer token is required."),
            @ApiResponse(
                    responseCode = "403",
                    description = "ASSESSMENT_READ and a granting role are required."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No assessment exists for the supplied transaction ID.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PreAuthorize("@fraudAuthorizationPolicy.hasPermissionAndGrantingRole(authentication, "
            + "T(com.odwambombo.fraudruleengine.shared.security.FraudPermission)"
            + ".ASSESSMENT_READ)")
    public ResponseEntity<FraudAssessmentResponse> getLatestByTransactionId(
            @Parameter(description = "Transaction identifier from the source system.")
            @PathVariable @NotBlank @ApiIdentifier String transactionId) {
        final FraudAssessment fraudAssessment = queryService.getLatestByTransactionId(transactionId);
        final FraudAssessmentResponse assessmentResponse = FraudAssessmentApiMapper.toFraudAssessmentResponse(fraudAssessment);
        logSuccessfulAssessmentLookup(
                "get_latest_assessment_by_transaction",
                assessmentResponse.assessmentId()
        );
        return ResponseEntity.ok(assessmentResponse);
    }

    @GetMapping
    @Operation(
            summary = "Search fraud assessments",
            description = "Combines all supplied filters with AND and returns deterministic "
                    + "newest-first summaries. The from and to boundaries apply to evaluatedAt, "
                    + "not the source transaction time. Page size is limited to 100."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A page of fraud-assessment summaries.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A filter, date boundary, or pagination value is invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "A valid bearer token is required."),
            @ApiResponse(
                    responseCode = "403",
                    description = "ASSESSMENT_READ and a granting role are required."
            )
    })
    @PreAuthorize("@fraudAuthorizationPolicy.hasPermissionAndGrantingRole(authentication, "
            + "T(com.odwambombo.fraudruleengine.shared.security.FraudPermission)"
            + ".ASSESSMENT_READ)")
    public ResponseEntity<PageResponse<FraudAssessmentSummaryResponse>> search(
            @Parameter(description = "Exact customer identifier.")
            @RequestParam(required = false) @ApiIdentifier String customerId,
            @Parameter(description = "Risk classification: LOW, MEDIUM, HIGH, or CRITICAL.")
            @RequestParam(required = false) RiskLevel riskLevel,
            @Parameter(description = "Whether the assessment crossed the configured flag threshold.")
            @RequestParam(required = false) Boolean flagged,
            @Parameter(description = "Inclusive evaluatedAt lower boundary as a date or offset date-time.")
            @RequestParam(required = false) String from,
            @Parameter(description = "Inclusive evaluatedAt upper boundary as a date or offset date-time.")
            @RequestParam(required = false) String to,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100.", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        final Instant instantFrom = parseTimeBoundary(from, false, "from");
        final Instant instantTo = parseTimeBoundary(to, true, "to");
        final FraudAssessmentQuery fraudAssessmentQuery = new FraudAssessmentQuery(
                customerId,
                riskLevel,
                flagged,
                instantFrom,
                instantTo,
                page,
                size);
        final Page<FraudAssessmentSummary> assessments = queryService.search(fraudAssessmentQuery);
        final Page<FraudAssessmentSummaryResponse> assessmentResponses = assessments.map(FraudAssessmentApiMapper::toFraudAssessmentSummaryResponse);
        final PageResponse<FraudAssessmentSummaryResponse> pageResponse = PageResponse.from(assessmentResponses);
        LOGGER.atInfo()
                .addKeyValue("api.operation", "search_fraud_assessments")
                .addKeyValue("http.request.method", "GET")
                .addKeyValue("http.response.status_code", HttpStatus.OK.value())
                .addKeyValue("response.item_count", pageResponse.content().size())
                .addKeyValue("pagination.page.number", pageResponse.page())
                .addKeyValue("pagination.page.size", pageResponse.size())
                .log("Fraud assessment API request completed");
        return ResponseEntity.ok(pageResponse);
    }

    private void logSuccessfulAssessmentLookup(String apiOperation, UUID assessmentId) {
        LOGGER.atInfo()
                .addKeyValue("api.operation", apiOperation)
                .addKeyValue("http.request.method", "GET")
                .addKeyValue("http.response.status_code", HttpStatus.OK.value())
                .addKeyValue("fraud.assessment.id", assessmentId)
                .log("Fraud assessment API request completed");
    }

    private Instant parseTimeBoundary(String rawBoundary, boolean useEndOfDayBoundary, String queryParameterName) {
        if (rawBoundary == null) {
            return null;
        }
        try {
            if (rawBoundary.length() == 10) {
                final LocalDate date = LocalDate.parse(rawBoundary);
                if (useEndOfDayBoundary) {
                    return date.plusDays(1)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .minusNanos(1_000);
                }
                return date.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return OffsetDateTime.parse(rawBoundary).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidRequestException(queryParameterName + " must be an ISO-8601 date or offset date-time.");
        }
    }
}
