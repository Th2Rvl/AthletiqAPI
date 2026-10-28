package com.athletiq.performance.dto;

import com.athletiq.exercise.MuscleGroup;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Un exercice de l'instantané en prévu / réalisé (US28), nom et groupe musculaire copiés à la planification.
 * {@code bodyweight} : toutes ses charges prévues sont vides, ses volumes sont alors {@code null}. Volume =
 * Σ reps × charge, charge vide exclue ; une série non faite compte 0 dans le volume réalisé, qui est
 * {@code null} si la séance n'est pas réalisée. {@code volumeDeltaPercent} : écart arrondi à l'entier,
 * {@code null} si le volume prévu est {@code null} ou nul. {@code sets} est trié par position.
 */
public record ReviewExercise(
        UUID snapshotExerciseId,
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        int position,
        String note,
        boolean bodyweight,
        BigDecimal plannedVolumeKg,
        BigDecimal performedVolumeKg,
        Integer volumeDeltaPercent,
        List<ReviewSet> sets) {
}
