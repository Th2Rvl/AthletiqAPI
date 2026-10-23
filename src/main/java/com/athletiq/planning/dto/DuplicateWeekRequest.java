package com.athletiq.planning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Dupliquer la semaine qui commence le lundi {@code weekStart} sur les {@code weeks} semaines suivantes
 * (US26, Q10). La semaine source peut être passée ; les dates cibles, non.
 */
public record DuplicateWeekRequest(
        @NotNull(message = "La semaine source est obligatoire")
        @Monday(message = "La semaine source doit commencer un lundi")
        LocalDate weekStart,

        @NotNull(message = "Le nombre de semaines est obligatoire")
        @Min(value = 1, message = "Au moins 1 semaine")
        @Max(value = 12, message = "Au plus 12 semaines")
        Integer weeks) {
}
