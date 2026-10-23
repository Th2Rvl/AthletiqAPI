package com.athletiq.planning.dto;

import java.util.UUID;

/** Séance planifiée d'un membre dans une entrée du calendrier de groupe : ouvrir ou annuler se fait par {@code id}. */
public record GroupCalendarSession(UUID id, UUID athleteId, String firstName, String lastName) {
}
