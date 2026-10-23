package com.athletiq.workout.dto;

import java.util.List;

/**
 * Exercice de séance, sans identifiant exposé : le PUT recrée les exercices et les séries, le front
 * les suit avec ses propres clés. {@code sets} est trié par position.
 */
public record WorkoutExerciseResponse(int position, ExerciseRef exercise, String note, List<PlannedSetResponse> sets) {
}
