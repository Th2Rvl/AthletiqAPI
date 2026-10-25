package com.athletiq.planning.dto;

import com.athletiq.planning.SessionStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Séance planifiée dans le calendrier d'un athlète. {@code name} est celui de l'instantané ;
 * {@code locked} : passée ou réalisée (même future), le front masque alors Déplacer et Annuler.
 * {@code status} (#7) : « Réalisée » ({@code DONE}), « Non faite » ({@code MISSED}) ou à venir.
 */
public record PlannedSessionSummary(
        UUID id,
        UUID athleteId,
        LocalDate date,
        String name,
        long exerciseCount,
        long setCount,
        UUID groupId,
        boolean locked,
        SessionStatus status) {
}
