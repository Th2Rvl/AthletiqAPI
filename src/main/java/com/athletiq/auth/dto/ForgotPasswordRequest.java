package com.athletiq.auth.dto;

import com.athletiq.user.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Demande de réinitialisation du mot de passe (US08). */
public record ForgotPasswordRequest(
        @NotBlank(message = "L'e-mail est obligatoire")
        @Email(message = "L'e-mail n'est pas valide")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email) {

    public ForgotPasswordRequest {
        email = EmailNormalizer.normalize(email);
    }
}
