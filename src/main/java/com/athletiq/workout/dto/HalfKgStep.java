package com.athletiq.workout.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Charge multiple de 0,5 kg (Q2). {@code null} est valide : il signifie « poids du corps ». */
@Documented
@Constraint(validatedBy = HalfKgStepValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HalfKgStep {

    String message() default "La charge doit être un multiple de 0,5 kg";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
