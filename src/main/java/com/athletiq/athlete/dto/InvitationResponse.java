package com.athletiq.athlete.dto;

import java.time.Instant;
import java.util.UUID;

/** Invitation en attente. {@code code} est déchiffré à la volée, sans tiret (le front formate {@code XXXX-XXXX}). */
public record InvitationResponse(UUID id, String label, String code, Instant expiresAt, Instant createdAt) {
}
