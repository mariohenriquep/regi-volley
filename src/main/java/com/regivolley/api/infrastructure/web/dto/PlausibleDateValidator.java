package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/** The check behind {@link PlausibleDate}. */
public class PlausibleDateValidator implements ConstraintValidator<PlausibleDate, LocalDate> {

    static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    @Override
    public boolean isValid(LocalDate date, ConstraintValidatorContext context) {
        return date == null || (!date.isBefore(EARLIEST) && !date.isAfter(LATEST));
    }
}
