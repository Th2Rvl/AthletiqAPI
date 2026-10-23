package com.athletiq.athlete.dto;

import java.time.Instant;
import java.util.UUID;

/** Coach qui suit désormais l'athlète, après la saisie de son code. */
public record CoachLinkResponse(UUID coachId, String firstName, String lastName, Instant joinedAt) {
}
