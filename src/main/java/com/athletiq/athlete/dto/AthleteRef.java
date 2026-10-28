package com.athletiq.athlete.dto;

import java.util.UUID;

/** Athlète suivi par un coach, identité seulement : pour les autres fonctionnalités (tableau de bord, #7). */
public record AthleteRef(UUID athleteId, String firstName, String lastName) {
}
