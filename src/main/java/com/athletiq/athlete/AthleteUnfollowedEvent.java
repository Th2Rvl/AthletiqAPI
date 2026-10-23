package com.athletiq.athlete;

import java.util.UUID;

/**
 * Publié quand un coach cesse de suivre un athlète (RM5). Le package {@code group} l'écoute pour
 * retirer l'athlète des groupes de ce coach, dans la même transaction.
 */
public record AthleteUnfollowedEvent(UUID coachId, UUID athleteId) {
}
