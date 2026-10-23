package com.athletiq.planning.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Planifier une séance pour un athlète ou un groupe (US21, US22). {@code date} est un jour sans heure
 * ({@code AAAA-MM-JJ}) ; un format invalide est refusé par Jackson ({@code 400 MALFORMED_REQUEST}).
 */
public record CreatePlannedSessionRequest(
        @NotNull(message = "La séance est obligatoire")
        UUID workoutId,

        @NotNull(message = "La date est obligatoire")
        LocalDate date) {
}
