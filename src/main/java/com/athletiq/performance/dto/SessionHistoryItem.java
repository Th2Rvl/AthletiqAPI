package com.athletiq.performance.dto;

import com.athletiq.planning.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une séance de l'historique d'un athlète (US27, US29) : réalisée ({@code DONE}) ou non faite
 * ({@code MISSED}). Ressenti et dates de validation viennent de la réalisation validée : {@code null} pour une
 * séance non faite, ou non renseignés (jamais 0). {@code correctedAt} : « Modifiée le … ».
 */
public record SessionHistoryItem(
        UUID plannedSessionId,
        LocalDate date,
        String name,
        SessionStatus status,
        Integer rpe,
        Integer fatigue,
        boolean hasComment,
        Instant validatedAt,
        Instant correctedAt) {
}
