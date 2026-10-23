package com.athletiq.group;

import java.time.Instant;
import java.util.UUID;

/** Projection d'un groupe avec son nombre de membres, calculé par {@code GROUP BY} dans la requête de liste. */
public record GroupSummary(UUID id, String name, long athleteCount, Instant createdAt) {
}
