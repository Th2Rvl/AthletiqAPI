package com.athletiq.performance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligne d'historique (US27, US29) lue en une requête : la séance planifiée, le nom de son instantané et, si
 * elle est réalisée, sa réalisation validée ({@code performanceId} non vide). Le commentaire n'est pas
 * transporté, seulement sa présence.
 */
public record SessionHistoryRow(
        UUID plannedSessionId,
        LocalDate date,
        String name,
        UUID performanceId,
        Integer rpe,
        Integer fatigue,
        boolean hasComment,
        Instant validatedAt,
        Instant correctedAt) {

    public boolean done() {
        return performanceId != null;
    }
}
