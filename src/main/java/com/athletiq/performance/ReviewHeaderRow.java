package com.athletiq.performance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * En-tête du prévu / réalisé (US28, US29) : la séance planifiée, son instantané et, si elle est réalisée, le
 * ressenti de sa réalisation validée ({@code performanceId} non vide ; un brouillon reste invisible).
 */
public record ReviewHeaderRow(
        UUID plannedSessionId,
        UUID athleteId,
        LocalDate date,
        String name,
        String description,
        UUID performanceId,
        Integer rpe,
        Integer fatigue,
        String comment,
        Instant validatedAt,
        Instant correctedAt) {

    public boolean done() {
        return performanceId != null;
    }
}
