package com.athletiq.performance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Réalisé complet d'une séance : il remplace ce qui était enregistré. Une série prévue absente de
 * {@code sets} est non faite. Le ressenti est facultatif : {@code null} = non renseigné (jamais 0).
 */
public record SavePerformanceRequest(
        @NotNull(message = "Les séries sont obligatoires")
        @Size(max = 600, message = "Trop de séries")
        List<@Valid PerformedSetInput> sets,

        @Min(value = 1, message = "Le RPE va de 1 à 10")
        @Max(value = 10, message = "Le RPE va de 1 à 10")
        Integer rpe,

        @Min(value = 1, message = "La fatigue va de 1 à 5")
        @Max(value = 5, message = "La fatigue va de 1 à 5")
        Integer fatigue,

        @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères")
        String comment) {
}
