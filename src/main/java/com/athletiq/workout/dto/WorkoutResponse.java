package com.athletiq.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Séance complète (détail, création, remplacement, duplication). {@code exercises} est trié par position. */
public record WorkoutResponse(
        UUID id,
        String name,
        String description,
        List<WorkoutExerciseResponse> exercises,
        Instant createdAt,
        Instant updatedAt) {
}
