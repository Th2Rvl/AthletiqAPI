package com.athletiq.workout.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/** Vérifie {@link HalfKgStep} : le reste de la division par 0,5 est nul. */
public class HalfKgStepValidator implements ConstraintValidator<HalfKgStep, BigDecimal> {

    private static final BigDecimal STEP = new BigDecimal("0.5");

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return value == null || value.remainder(STEP).signum() == 0;
    }
}
