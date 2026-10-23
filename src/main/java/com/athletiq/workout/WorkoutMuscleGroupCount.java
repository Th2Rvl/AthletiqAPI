package com.athletiq.workout;

import com.athletiq.exercise.MuscleGroup;
import java.util.UUID;

/** Ligne d'agrégation de la liste : nombre d'exercices d'une séance par groupe musculaire. */
public record WorkoutMuscleGroupCount(UUID workoutId, MuscleGroup muscleGroup, long count) {
}
