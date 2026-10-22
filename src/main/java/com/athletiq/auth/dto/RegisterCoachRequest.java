package com.athletiq.auth.dto;

import com.athletiq.auth.PasswordPolicy;
import com.athletiq.user.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Création d'un compte coach (US06).
 *
 * <p>Il n'y a pas de champ {@code role} : le rôle est fixé par l'API (RM3). L'e-mail est
 * normalisé dans le constructeur compact, donc avant Bean Validation ; le mot de passe n'est
 * jamais modifié.
 */
public record RegisterCoachRequest(
        @NotBlank(message = "L'e-mail est obligatoire")
        @Email(message = "L'e-mail n'est pas valide")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "Le mot de passe doit contenir entre 12 et 72 caractères")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                message = "Le mot de passe doit contenir au moins une lettre et un chiffre")
        String password,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName) {

    public RegisterCoachRequest {
        email = EmailNormalizer.normalize(email);
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
    }

    /** Ne jamais afficher le mot de passe, même dans un log de debug. */
    @Override
    public String toString() {
        return "RegisterCoachRequest[email=" + email + ", password=***]";
    }
}
