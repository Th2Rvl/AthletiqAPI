package com.athletiq.performance.dto;

import com.athletiq.exercise.MuscleGroup;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Exercice réalisé par l'athlète dans au moins une séance réalisée de ce coach (choix de la progression, US30) :
 * au moins une de ses séries y est faite. Libellé et groupe musculaire de l'instantané le plus récent (RM6) ;
 * {@code sessionCount} séances réalisées, la dernière le {@code lastPerformedOn}. La liste est triée par groupe
 * musculaire (ordre alphabétique de son code) puis par nom.
 */
public record PerformedExerciseResponse(
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        int sessionCount,
        LocalDate lastPerformedOn) {
}
