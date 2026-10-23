package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Séance planifiée dans le calendrier d'un athlète. {@code name} est celui de l'instantané ;
 * {@code locked} : passée ou réalisée, le front masque alors Déplacer et Annuler.
 */
public record PlannedSessionSummary(
        UUID id,
        UUID athleteId,
        LocalDate date,
        String name,
        long exerciseCount,
        long setCount,
        UUID groupId,
        boolean locked) {
}
