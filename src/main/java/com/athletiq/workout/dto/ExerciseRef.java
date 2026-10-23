package com.athletiq.workout.dto;

import com.athletiq.exercise.MuscleGroup;
import java.util.UUID;

/** Exercice de bibliothèque tel que référencé par une séance. */
public record ExerciseRef(UUID id, String name, MuscleGroup muscleGroup) {
}
