package com.athletiq.performance.dto;

import com.athletiq.performance.PerformanceStatus;
import java.time.Instant;
import java.util.List;

/**
 * Réalisé de l'athlète pour une séance. {@code status} {@code null} = rien d'enregistré encore ; sinon
 * {@code DRAFT} (saisie en cours, invisible du coach) ou {@code VALIDATED}. {@code correctedAt} : dernière
 * correction après validation.
 */
public record AthletePerformanceResponse(
        PerformanceStatus status,
        Integer rpe,
        Integer fatigue,
        String comment,
        Instant validatedAt,
        Instant correctedAt,
        List<PerformedSetValue> sets) {

    public static AthletePerformanceResponse none() {
        return new AthletePerformanceResponse(null, null, null, null, null, null, List.of());
    }
}
