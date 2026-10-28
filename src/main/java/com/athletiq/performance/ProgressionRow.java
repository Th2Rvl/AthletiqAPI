package com.athletiq.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un exercice dans une séance réalisée, toutes ses séries faites agrégées (exercice répété dans la séance
 * compris) : charge max, répétitions max et volume (Σ reps × charge, charge vide exclue ; vide sans série
 * chargée).
 */
public record ProgressionRow(
        UUID plannedSessionId,
        LocalDate date,
        Instant createdAt,
        BigDecimal maxLoadKg,
        Integer maxReps,
        BigDecimal volumeKg) {
}
