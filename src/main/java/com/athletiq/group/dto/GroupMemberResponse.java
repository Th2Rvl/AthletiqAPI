package com.athletiq.group.dto;

import java.time.Instant;
import java.util.UUID;

/** Membre d'un groupe : identité de l'athlète et date d'ajout au groupe. */
public record GroupMemberResponse(UUID athleteId, String firstName, String lastName, String email, Instant addedAt) {
}
