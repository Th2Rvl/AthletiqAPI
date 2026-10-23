package com.athletiq.auth.dto;

import com.athletiq.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Changement de mot de passe à partir d'un jeton de réinitialisation (US08). */
public record ResetPasswordRequest(
        @NotBlank(message = "Le jeton est obligatoire")
        @Size(max = 128, message = "Le jeton n'est pas valide")
        String token,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "Le mot de passe doit contenir entre 12 et 72 caractères")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                message = "Le mot de passe doit contenir au moins une lettre et un chiffre")
        String password,

        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        @Size(max = PasswordPolicy.MAX_LENGTH, message = "La confirmation n'est pas valide")
        String passwordConfirmation) {

    /** Ne jamais afficher les mots de passe, même dans un log de debug. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, password=***]";
    }
}
