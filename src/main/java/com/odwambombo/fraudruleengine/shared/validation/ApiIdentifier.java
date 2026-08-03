package com.odwambombo.fraudruleengine.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates identifiers accepted at the HTTP API boundary.
 *
 * <p>Like the Jakarta constraints it composes, this annotation considers
 * {@code null} valid so callers can use it for optional parameters. Add
 * {@code @NotBlank} when an identifier is required.</p>
 */
@Documented
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Size(min = 1, max = 100)
@Pattern(regexp = ApiIdentifier.URL_SAFE_PATTERN)
@Target({
        ElementType.ANNOTATION_TYPE,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiIdentifier {

    String URL_SAFE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]*";
    String DEFAULT_MESSAGE = "must be 1 to 100 characters and contain only URL-safe letters, "
            + "numbers, '.', '_', ':' or '-'";

    String message() default DEFAULT_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
