package com.athletiq.organisation.dto;

import com.athletiq.user.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invitation d'un coach par e-mail, normalisé (trim + minuscules) avant tout contrôle. */
public record InviteCoachRequest(
        @NotBlank(message = "L'e-mail est obligatoire")
        @Email(message = "L'e-mail n'est pas valide")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email) {

    public InviteCoachRequest {
        email = EmailNormalizer.normalize(email);
    }
}
