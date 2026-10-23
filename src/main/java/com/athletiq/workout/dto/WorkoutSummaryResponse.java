package com.athletiq.workout.dto;

import com.athletiq.exercise.MuscleGroup;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Élément de la liste des séances (Q16).
 *
 * @param exerciseCount nombre d'exercices de séance, doublons compris
 * @param muscleGroups  groupes travaillés, distincts, dans l'ordre de déclaration de {@link MuscleGroup}
 */
public record WorkoutSummaryResponse(
        UUID id,
        String name,
        String description,
        long exerciseCount,
        List<MuscleGroup> muscleGroups,
        Instant createdAt,
        Instant updatedAt) {
}
