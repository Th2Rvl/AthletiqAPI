package com.athletiq.exercise.dto;

import com.athletiq.exercise.MuscleGroup;
import java.time.Instant;
import java.util.UUID;

/** Exercice tel qu'exposé à l'API. L'entité JPA ne sort jamais du service. */
public record ExerciseResponse(
        UUID id,
        String name,
        String description,
        MuscleGroup muscleGroup,
        String equipment,
        String videoUrl,
        Instant createdAt,
        Instant updatedAt) {
}
