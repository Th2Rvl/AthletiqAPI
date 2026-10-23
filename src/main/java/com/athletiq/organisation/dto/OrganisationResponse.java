package com.athletiq.organisation.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code athleteCount} vaut toujours 0 tant que la relation coach-athlète (#8) n'existe pas. */
public record OrganisationResponse(
        UUID id,
        String name,
        String description,
        long coachCount,
        long athleteCount,
        long pendingInvitationCount,
        Instant createdAt,
        Instant updatedAt) {
}
