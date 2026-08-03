package com.odwambombo.fraudruleengine.transaction.api;

import com.odwambombo.fraudruleengine.assessment.api.FraudAssessmentApiMapper;
import com.odwambombo.fraudruleengine.assessment.api.FraudAssessmentResponse;
import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;
import com.odwambombo.fraudruleengine.shared.exception.ApiErrorResponse;
import com.odwambombo.fraudruleengine.transaction.application.ProcessTransactionCommand;
import com.odwambombo.fraudruleengine.transaction.application.ProcessTransactionResult;
import com.odwambombo.fraudruleengine.transaction.application.TransactionProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/transaction-events")
@Tag(
        name = "Transaction events",
        description = "Ingests categorized transaction events and returns their fraud decisions."
)
@SecurityRequirement(name = "bearerAuth")
public class TransactionEventController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionEventController.class);

    private final TransactionProcessingService processingService;

    public TransactionEventController(TransactionProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping
    @Operation(
            summary = "Assess a transaction event",
            description = "Validates and evaluates a transaction using every enabled fraud rule, "
                    + "then atomically stores the event, assessment, and matched-rule reasons. "
                    + "The eventId is an idempotency key: an identical replay returns the original "
                    + "assessment, while different data for the same eventId is rejected."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "A new event was assessed and stored.",
                    content = @Content(schema = @Schema(implementation = FraudAssessmentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "An identical eventId replay returned the existing assessment.",
                    content = @Content(schema = @Schema(implementation = FraudAssessmentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The transaction event failed request validation.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "A valid bearer token is required."),
            @ApiResponse(
                    responseCode = "403",
                    description = "TRANSACTION_WRITE and a granting role are required."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The eventId was reused with different transaction data.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Transient transaction-processing retries were exhausted.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PreAuthorize("@fraudAuthorizationPolicy.hasPermissionAndGrantingRole(authentication, "
            + "T(com.odwambombo.fraudruleengine.shared.security.FraudPermission)"
            + ".TRANSACTION_WRITE)")
    public ResponseEntity<FraudAssessmentResponse> processTransactionEvent(@Valid @RequestBody TransactionEventRequest transactionEventRequest) {
        final ProcessTransactionCommand transactionCommand = ProcessTransactionCommand.fromRequest(transactionEventRequest);
        final ProcessTransactionResult processingResult = processingService.processTransaction(transactionCommand);
        final FraudAssessment fraudAssessment = processingResult.assessment();
        final FraudAssessmentResponse assessmentResponse = FraudAssessmentApiMapper.toFraudAssessmentResponse(fraudAssessment);
        if (!processingResult.created()) {
            logSuccessfulProcessing(assessmentResponse, false, HttpStatus.OK.value());
            return ResponseEntity.ok(assessmentResponse);
        }
        final URI location = URI.create("/api/v1/fraud-assessments/" + assessmentResponse.assessmentId());
        logSuccessfulProcessing(assessmentResponse, true, HttpStatus.CREATED.value());
        return ResponseEntity.created(location).body(assessmentResponse);
    }

    private void logSuccessfulProcessing(FraudAssessmentResponse assessmentResponse, boolean transactionCreated, int responseStatusCode) {
        LOGGER.atInfo()
                .addKeyValue("api.operation", "process_transaction_event")
                .addKeyValue("http.request.method", "POST")
                .addKeyValue("http.response.status_code", responseStatusCode)
                .addKeyValue("fraud.assessment.id", assessmentResponse.assessmentId())
                .addKeyValue("transaction.created", transactionCreated)
                .log("Transaction event API request completed");
    }
}
