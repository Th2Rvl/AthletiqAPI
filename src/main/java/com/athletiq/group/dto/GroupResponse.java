package com.athletiq.group.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(UUID id, String name, long athleteCount, Instant createdAt) {
}
