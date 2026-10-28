package com.athletiq.performance.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Indicateurs d'un athlète sur la période (US32) : séances passées réalisées et non faites, taux de réalisation
 * (entier, {@code null} sans séance passée : « - »), RPE moyen à une décimale ({@code null} sans RPE), non faites
 * des derniers jours ({@code missed-days}, 7 par défaut) et {@code watch} (« À surveiller »).
 */
public record AthleteKpi(
        UUID athleteId,
        String firstName,
        String lastName,
        int doneCount,
        int missedCount,
        Integer completionRate,
        BigDecimal averageRpe,
        int missedLast7Days,
        boolean watch) {
}
