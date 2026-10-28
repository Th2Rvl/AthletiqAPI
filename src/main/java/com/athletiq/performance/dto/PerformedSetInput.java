package com.athletiq.performance.dto;

import com.athletiq.workout.dto.HalfKgStep;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Réalisé d'une série prévue. {@code reps} {@code null} = série non faite ; {@code loadKg} {@code null} =
 * poids du corps.
 */
public record PerformedSetInput(
        @NotNull(message = "La série est obligatoire")
        UUID snapshotSetId,

        @Min(value = 1, message = "Au moins 1 répétition")
        @Max(value = 100, message = "Au plus 100 répétitions")
        Integer reps,

        @DecimalMin(value = "0.0", message = "La charge ne peut pas être négative")
        @DecimalMax(value = "999.5", message = "La charge ne doit pas dépasser 999,5 kg")
        @HalfKgStep(message = "La charge doit être un multiple de 0,5 kg")
        BigDecimal loadKg) {
}
