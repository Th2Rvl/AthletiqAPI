package com.athletiq.performance;

import com.athletiq.exercise.MuscleGroup;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une série prévue de l'instantané, avec l'exercice qui la porte et, en face, la série réalisée de la
 * réalisation validée (vide si la séance n'est pas réalisée, si la série n'a pas de ligne, ou si elle n'est
 * pas faite). Les champs de série sont vides pour un exercice sans série (aucun en pratique).
 */
public record ReviewLineRow(
        UUID snapshotExerciseId,
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        int exercisePosition,
        String note,
        UUID snapshotSetId,
        Integer setPosition,
        Integer plannedReps,
        BigDecimal plannedLoadKg,
        Integer restSeconds,
        Integer performedReps,
        BigDecimal performedLoadKg) {
}
