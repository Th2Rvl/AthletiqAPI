package com.athletiq.planning.dto;

import com.athletiq.exercise.MuscleGroup;
import java.util.List;
import java.util.UUID;

/** Exercice prévu, avec la note du coach et ses séries dans l'ordre. */
public record AthleteSessionExercise(
        UUID id,
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        int position,
        String note,
        List<AthleteSessionSet> sets) {
}
