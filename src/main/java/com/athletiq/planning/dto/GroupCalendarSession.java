package com.athletiq.planning.dto;

import com.athletiq.planning.SessionStatus;
import java.util.UUID;

/**
 * Séance planifiée d'un membre dans une entrée du calendrier de groupe : ouvrir ou annuler se fait par {@code id}.
 * {@code status} (#7) est propre à cette séance : un membre peut l'avoir réalisée et un autre non.
 */
public record GroupCalendarSession(UUID id, UUID athleteId, String firstName, String lastName, SessionStatus status) {
}
