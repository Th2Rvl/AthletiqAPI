package com.athletiq.performance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une séance réalisée dans la progression d'un exercice (US30), séries faites agrégées si l'exercice est
 * répété : charge max, répétitions max et volume (Σ reps × charge). {@code maxLoadKg} et {@code volumeKg}
 * sont {@code null} si aucune série faite n'est chargée (poids du corps). Kilos à une décimale.
 */
public record ProgressionPoint(
        UUID plannedSessionId,
        LocalDate date,
        BigDecimal maxLoadKg,
        Integer maxReps,
        BigDecimal volumeKg) {
}
