package com.athletiq.performance;

import com.athletiq.exercise.MuscleGroup;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Séries faites d'un exercice dans une séance réalisée, regroupées par charge ({@code loadKg} vide = poids du
 * corps) avec leur meilleur nombre de répétitions. Le libellé et le groupe musculaire sont ceux copiés dans
 * l'instantané de cette séance (RM6). Sert aux exercices réalisés (US30) et aux records (US31).
 */
public record PerformedSetGroupRow(
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        UUID plannedSessionId,
        LocalDate date,
        Instant createdAt,
        BigDecimal loadKg,
        Integer maxReps) {
}
