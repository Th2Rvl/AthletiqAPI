package com.athletiq.exercise;

import java.util.UUID;

/**
 * Usages d'un exercice dans les séances, pour refuser sa suppression (RM9, {@code EXERCISE_IN_USE}).
 * L'interface appartient à {@code exercise} et est implémentée par {@code workout} : ainsi
 * {@code workout} dépend de {@code exercise}, jamais l'inverse.
 */
public interface ExerciseUsage {

    /** Nombre de séances distinctes qui utilisent cet exercice (0 s'il n'est dans aucune). */
    long countWorkoutsUsing(UUID exerciseId);
}
