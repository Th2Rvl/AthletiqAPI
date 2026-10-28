package com.athletiq.performance.dto;

import com.athletiq.exercise.MuscleGroup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Record d'un exercice (US31, Q10), calculé à la lecture (RM7) sur les séries faites des séances réalisées de ce
 * coach : la meilleure charge, avec les répétitions de cette série et sa date ; à égalité, plus de répétitions,
 * puis la date la plus ancienne (le premier jour où le record a été atteint). Poids du corps
 * ({@code bodyweight}, aucune série faite chargée) : répétitions max, {@code loadKg} {@code null}.
 */
public record PersonalRecord(
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        boolean bodyweight,
        BigDecimal loadKg,
        int reps,
        LocalDate date,
        UUID plannedSessionId) {
}
