package com.athletiq.planning.dto;

import com.athletiq.planning.SessionStatus;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Détail d'une séance planifiée (lecture, création pour un athlète, déplacement). Le contenu vient de
 * l'instantané, avec des exercices de même forme qu'une séance ({@code exercise.name} et
 * {@code muscleGroup} copiés), triés par position. {@code locked} : passée ou réalisée (même future) ;
 * {@code status} (#7) : {@code DONE} ouvre « Voir le réalisé ».
 */
public record PlannedSessionDetailResponse(
        UUID id,
        UUID athleteId,
        String athleteFirstName,
        String athleteLastName,
        LocalDate date,
        UUID groupId,
        UUID sourceWorkoutId,
        boolean locked,
        SessionStatus status,
        String name,
        String description,
        List<WorkoutExerciseResponse> exercises,
        Instant createdAt,
        Instant updatedAt) {
}
