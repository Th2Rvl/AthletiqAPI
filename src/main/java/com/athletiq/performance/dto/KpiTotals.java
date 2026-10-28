package com.athletiq.performance.dto;

import java.math.BigDecimal;

/**
 * En-tête du tableau de bord : les indicateurs de tous les athlètes listés, cumulés (et non moyennés) :
 * taux = Σ réalisées / Σ (réalisées + non faites), RPE moyen sur toutes les séances réalisées avec RPE.
 */
public record KpiTotals(
        int athleteCount,
        int doneCount,
        int missedCount,
        Integer completionRate,
        BigDecimal averageRpe) {
}
