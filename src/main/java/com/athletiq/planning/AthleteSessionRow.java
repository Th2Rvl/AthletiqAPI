package com.athletiq.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Ligne du calendrier de l'athlète, lue en une requête sans charger les instantanés. */
public record AthleteSessionRow(
        UUID id,
        LocalDate date,
        UUID snapshotId,
        String name,
        UUID coachId,
        String coachFirstName,
        String coachLastName,
        Instant createdAt) {
}
