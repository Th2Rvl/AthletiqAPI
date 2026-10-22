package com.athletiq.profile.dto;

import com.athletiq.user.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * Profil du coach connecté (US09). Le mot de passe n'y figure jamais : seuls ses
 * informations d'identification et son rôle.
 */
public record CoachProfileResponse(UUID id, String email, String firstName, String lastName, Role role,
                                   Instant createdAt) {
}
