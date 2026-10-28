package com.athletiq.planning.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Série prévue. {@code loadKg} {@code null} = poids du corps, {@code restSeconds} {@code null} = non précisé. */
public record AthleteSessionSet(UUID id, int position, int reps, BigDecimal loadKg, Integer restSeconds) {
}
