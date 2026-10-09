package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A date a client typed that must lie between 2000-01-01 and 2100-12-31 (threat model S4): no week, subscription or payment is built
 * from year 1 or year 999999999. A missing date is left to {@code @NotNull}. Answered with a 400 naming the field.
 */
@Documented
@Constraint(validatedBy = PlausibleDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlausibleDate {

    String message() default "must be a date between 2000-01-01 and 2100-12-31";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
