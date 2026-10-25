package com.athletiq.performance;

import com.athletiq.exercise.MuscleGroup;

/** Libellé d'un exercice : celui copié dans un instantané, ou à défaut celui de la bibliothèque. */
public record ExerciseLabel(String exerciseName, MuscleGroup muscleGroup) {
}
