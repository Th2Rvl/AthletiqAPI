package com.athletiq.workout.dto;

import java.math.BigDecimal;

/** Série prévue. {@code loadKg} et {@code restSeconds} peuvent être {@code null}. */
public record PlannedSetResponse(int position, int reps, BigDecimal loadKg, Integer restSeconds) {
}
