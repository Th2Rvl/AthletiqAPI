package com.athletiq.performance.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une série en prévu / réalisé (US28). Le prévu vient de l'instantané. {@code done} : la séance est réalisée
 * et cette série faite ; sinon tout le réalisé est {@code null} (« Non faite », ou séance non réalisée).
 * {@code repsDelta = performedReps - plannedReps} ; {@code loadDeltaKg} est {@code null} si l'une des charges
 * est vide (poids du corps). Kilos à une décimale.
 */
public record ReviewSet(
        UUID snapshotSetId,
        int position,
        int plannedReps,
        BigDecimal plannedLoadKg,
        Integer restSeconds,
        boolean done,
        Integer performedReps,
        BigDecimal performedLoadKg,
        Integer repsDelta,
        BigDecimal loadDeltaKg) {
}
