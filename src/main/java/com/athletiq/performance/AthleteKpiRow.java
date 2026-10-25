package com.athletiq.performance;

import java.util.UUID;

/**
 * Agrégat d'un athlète sur la période du tableau de bord (US32), en une requête groupée par athlète : séances
 * passées réalisées et non faites, non faites récentes, et somme et nombre des RPE renseignés des réalisées
 * (pour une moyenne exacte, y compris tous athlètes confondus).
 */
public record AthleteKpiRow(
        UUID athleteId,
        long doneCount,
        long missedCount,
        long missedRecently,
        Long rpeSum,
        long rpeCount) {
}
