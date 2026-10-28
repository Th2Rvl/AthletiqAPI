package com.athletiq.performance.dto;

import com.athletiq.exercise.MuscleGroup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Record personnel de l'athlète (US16) avec le record qu'il a battu : le meilleur résultat des séances
 * antérieures à la sienne. {@code previousReps} {@code null} = premier record de l'exercice ;
 * {@code previousLoadKg} {@code null} = ancien record au poids du corps (ou premier record).
 */
public record AthletePersonalRecord(
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        boolean bodyweight,
        BigDecimal loadKg,
        int reps,
        LocalDate date,
        UUID plannedSessionId,
        BigDecimal previousLoadKg,
        Integer previousReps) {
}
