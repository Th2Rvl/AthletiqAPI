package com.athletiq.user.dto;

import com.athletiq.user.Role;
import java.util.UUID;

/** Vue publique d'un compte : jamais de hash de mot de passe. */
public record UserResponse(UUID id, String email, String firstName, String lastName, Role role) {
}
