package com.athletiq.performance.dto;

import com.athletiq.planning.SessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Prévu / réalisé d'une séance planifiée (US28) et son ressenti (US29). Le prévu vient toujours de
 * l'instantané (RM5), même si le modèle a changé ou disparu. Hors {@code DONE}, le front affiche le prévu seul
 * avec « Séance non faite » ou « À venir » : {@code feedback} est alors {@code null} et aucune série n'est faite.
 * {@code exercises} est trié par position.
 */
public record SessionReviewResponse(
        UUID plannedSessionId,
        UUID athleteId,
        String athleteFirstName,
        String athleteLastName,
        LocalDate date,
        LocalDate today,
        String name,
        String description,
        SessionStatus status,
        SessionFeedback feedback,
        List<ReviewExercise> exercises) {
}
