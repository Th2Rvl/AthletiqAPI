package com.athletiq.performance.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Réalisé enregistré d'une série prévue. */
public record PerformedSetValue(UUID snapshotSetId, Integer reps, BigDecimal loadKg) {
}
