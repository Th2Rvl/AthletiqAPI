package com.athletiq.group.dto;

import java.util.UUID;

/** Membre actuel d'un groupe, pour la planification (#6) : identité seulement. */
public record GroupMemberRef(UUID athleteId, String firstName, String lastName) {
}
