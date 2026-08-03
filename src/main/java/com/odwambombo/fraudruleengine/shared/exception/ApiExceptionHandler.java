package com.odwambombo.fraudruleengine.shared.exception;

import com.odwambombo.fraudruleengine.shared.persistence.DatabaseConstraintClassifier;
import com.odwambombo.fraudruleengine.shared.observability.ApplicationAlertSignals;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;
    private final ApplicationAlertSignals alertSignals;

    public ApiExceptionHandler(Clock clock, ApplicationAlertSignals alertSignals) {
        this.clock = clock;
        this.alertSignals = alertSignals;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidMethodArguments(MethodArgumentNotValidException validationException) {
        final Map<String, String> fieldErrors = new LinkedHashMap<>();
        validationException.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .forEach(fieldError -> fieldErrors.putIfAbsent(
                        fieldError.getField(),
                        resolveValidationMessage(fieldError)
                ));

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSACTION_EVENT",
                "The transaction event is invalid.",
                fieldErrors,
                validationException
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            InvalidRequestException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception invalidRequestException) {
        final String errorMessage = invalidRequestException instanceof InvalidRequestException
                ? invalidRequestException.getMessage()
                : "The request contains an invalid value.";
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                errorMessage,
                Map.of(),
                invalidRequestException
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException notFoundException) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "ASSESSMENT_NOT_FOUND",
                notFoundException.getMessage(),
                Map.of(),
                notFoundException
        );
    }

    @ExceptionHandler(ConflictingEventException.class)
    ResponseEntity<ApiErrorResponse> handleEventConflict(
            ConflictingEventException eventConflictException) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "EVENT_ID_CONFLICT",
                eventConflictException.getMessage(),
                Map.of(),
                eventConflictException
        );
    }

    @ExceptionHandler(ProcessingTemporarilyUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleProcessingTemporarilyUnavailable(
            ProcessingTemporarilyUnavailableException processingException) {
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PROCESSING_RETRY_EXHAUSTED",
                "Transaction processing is temporarily unavailable; retry the request.",
                Map.of(),
                processingException
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException dataIntegrityException) {
        if (!DatabaseConstraintClassifier.isCausedByConstraint(
                dataIntegrityException,
                DatabaseConstraintClassifier.EVENT_ID_UNIQUE_CONSTRAINT
        )) {
            alertSignals.recordUnhandledApplicationException(dataIntegrityException);
            return buildErrorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PERSISTENCE_ERROR",
                    "The fraud assessment could not be stored.",
                    Map.of(),
                    dataIntegrityException
            );
        }
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "DUPLICATE_EVENT",
                "The transaction event has already been processed.",
                Map.of(),
                dataIntegrityException
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException accessDeniedException) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "The authenticated user is not permitted to perform this operation.",
                Map.of(),
                accessDeniedException
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnhandledException(Exception unhandledException) {
        alertSignals.recordUnhandledApplicationException(unhandledException);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred while processing the request.",
                Map.of(),
                unhandledException
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus httpStatus,
            String errorCode,
            String errorMessage,
            Map<String, String> fieldErrors,
            Throwable handledException) {
        logHandledApiError(httpStatus, errorCode, handledException);
        return ResponseEntity.status(httpStatus)
                .body(new ApiErrorResponse(
                        errorCode,
                        errorMessage,
                        Instant.now(clock),
                        fieldErrors
                ));
    }

    private void logHandledApiError(HttpStatus httpStatus, String errorCode, Throwable handledException) {
        final String exceptionType = handledException == null ? "Unknown" : handledException.getClass().getSimpleName();
        if (httpStatus.is5xxServerError()) {
            LOGGER.atError()
                    .addKeyValue("api.error.code", errorCode)
                    .addKeyValue("http.response.status_code", httpStatus.value())
                    .addKeyValue("exception.type", exceptionType)
                    .log("API request failed");
            return;
        }
        LOGGER.atWarn()
                .addKeyValue("api.error.code", errorCode)
                .addKeyValue("http.response.status_code", httpStatus.value())
                .addKeyValue("exception.type", exceptionType)
                .log("API request rejected");
    }

    private String resolveValidationMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() == null ? "is invalid" : fieldError.getDefaultMessage();
    }

}
