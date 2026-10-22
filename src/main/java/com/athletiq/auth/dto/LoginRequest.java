package com.athletiq.auth.dto;

import com.athletiq.user.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Identifiants de connexion. L'e-mail est normalisé dans le constructeur compact (donc avant
 * Bean Validation) ; le mot de passe n'est jamais modifié.
 */
public record LoginRequest(
        @NotBlank(message = "L'e-mail est obligatoire")
        @Email(message = "L'e-mail n'est pas valide")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(max = 72, message = "Le mot de passe ne doit pas dépasser 72 caractères")
        String password) {

    public LoginRequest {
        email = EmailNormalizer.normalize(email);
    }

    /** Ne jamais afficher le mot de passe, même dans un log de debug. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
