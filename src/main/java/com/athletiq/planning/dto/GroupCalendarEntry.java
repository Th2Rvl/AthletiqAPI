package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Séances d'un même instantané le même jour, pour les membres actuels du groupe : « nom, n/N athlètes ».
 *
 * @param athleteCount membres actuels ayant cette séance ce jour-là (n)
 * @param memberCount  membres actuels du groupe (N)
 * @param locked       jour passé, ou toutes les séances de l'entrée réalisées (#7) ; sinon, chaque séance
 *                     porte son propre {@code status}
 * @param sessions     triées par nom puis prénom
 */
public record GroupCalendarEntry(
        LocalDate date,
        UUID snapshotId,
        String name,
        int athleteCount,
        int memberCount,
        boolean locked,
        List<GroupCalendarSession> sessions) {
}
