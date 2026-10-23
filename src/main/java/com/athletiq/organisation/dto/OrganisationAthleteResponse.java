package com.athletiq.organisation.dto;

import java.util.List;
import java.util.UUID;

/** Athlète suivi par au moins un coach membre (alimenté à partir de #8). */
public record OrganisationAthleteResponse(
        UUID athleteId,
        String firstName,
        String lastName,
        List<AthleteCoach> coaches) {

    public record AthleteCoach(UUID coachId, String firstName, String lastName) {
    }
}
