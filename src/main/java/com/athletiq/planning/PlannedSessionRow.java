package com.athletiq.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligne de calendrier ou de duplication, lue en une requête sans charger les instantanés : la séance
 * planifiée, son instantané (identifiant et nom) et ses colonnes d'affichage.
 */
public record PlannedSessionRow(
        UUID id,
        UUID athleteId,
        LocalDate date,
        UUID snapshotId,
        String name,
        UUID groupId,
        UUID sourceWorkoutId,
        Instant createdAt) {
}
