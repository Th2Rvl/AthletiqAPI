package com.athletiq.exercise;

import java.util.UUID;

/**
 * Usages d'un exercice dans les séances planifiées (Q11 de #6, {@code EXERCISE_IN_USE}). Second port,
 * implémenté par {@code planning} : {@code planning} dépend de {@code exercise}, jamais l'inverse.
 */
public interface PlannedExerciseUsage {

    /** Nombre de séances planifiées distinctes (passées comprises) dont l'instantané contient cet exercice. */
    long countPlannedSessionsUsing(UUID exerciseId);
}
