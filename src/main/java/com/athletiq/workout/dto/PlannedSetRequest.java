package com.athletiq.workout.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Série prévue (RM4). Sa position est son index dans la liste {@code sets} : aucun champ
 * {@code position} n'est lu.
 *
 * @param loadKg      en kg, au pas de 0,5 ; {@code null} = poids du corps
 * @param restSeconds en secondes ; {@code null} = non précisé
 */
public record PlannedSetRequest(
        @NotNull(message = "Les répétitions sont obligatoires")
        @Min(value = 1, message = "Au moins 1 répétition")
        @Max(value = 100, message = "Au plus 100 répétitions")
        Integer reps,

        @DecimalMin(value = "0.0", message = "La charge ne peut pas être négative")
        @DecimalMax(value = "999.5", message = "La charge ne doit pas dépasser 999,5 kg")
        @HalfKgStep(message = "La charge doit être un multiple de 0,5 kg")
        BigDecimal loadKg,

        @Min(value = 0, message = "Le repos ne peut pas être négatif")
        @Max(value = 600, message = "Le repos ne doit pas dépasser 600 secondes")
        Integer restSeconds) {
}
