package com.athletiq.organisation.dto;

import com.athletiq.organisation.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

/** {@code coachId} est renseigné quand l'invitation est ACCEPTED, {@code null} sinon. */
public record InvitationResponse(
        UUID id,
        UUID organisationId,
        String email,
        InvitationStatus status,
        UUID coachId,
        Instant createdAt,
        Instant updatedAt) {
}
