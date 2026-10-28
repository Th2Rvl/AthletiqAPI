package com.athletiq.performance.dto;

import com.athletiq.exercise.MuscleGroup;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Progression d'un exercice pour un athlète (US30) sur {@code weeks} semaines : séances réalisées de ce coach
 * datées à partir de {@code from} ({@code today - weeks × 7}, sans borne haute : Q17). {@code points} est trié
 * par date croissante, vide si l'exercice n'a pas été réalisé ; un seul point affiche « Pas encore assez de
 * données ». {@code bodyweight} : aucune série faite chargée sur la période, le graphique suit alors les
 * répétitions max. Libellé de l'instantané le plus récent (RM6), ou de la bibliothèque s'il n'a jamais été
 * réalisé.
 */
public record ProgressionResponse(
        UUID athleteId,
        UUID exerciseId,
        String exerciseName,
        MuscleGroup muscleGroup,
        boolean bodyweight,
        int weeks,
        LocalDate from,
        LocalDate today,
        List<ProgressionPoint> points) {
}
