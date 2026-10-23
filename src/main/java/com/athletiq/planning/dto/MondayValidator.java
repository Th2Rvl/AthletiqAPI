package com.athletiq.planning.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.DayOfWeek;
import java.time.LocalDate;

/** Vérifie {@link Monday}. */
public class MondayValidator implements ConstraintValidator<Monday, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return value == null || value.getDayOfWeek() == DayOfWeek.MONDAY;
    }
}
