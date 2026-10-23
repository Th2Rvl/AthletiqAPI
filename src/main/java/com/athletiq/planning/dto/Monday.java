package com.athletiq.planning.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** La date doit être un lundi. {@code null} est valide (laissé à {@code @NotNull}). */
@Documented
@Constraint(validatedBy = MondayValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Monday {

    String message() default "La date doit être un lundi";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
