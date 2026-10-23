package com.athletiq.organisation.dto;

import java.time.Instant;
import java.util.UUID;

public record OrganisationCoachResponse(
        UUID coachId,
        String firstName,
        String lastName,
        String email,
        Instant joinedAt) {
}
