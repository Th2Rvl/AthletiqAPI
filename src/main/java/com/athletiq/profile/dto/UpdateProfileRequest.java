package com.athletiq.profile.dto;

import com.athletiq.user.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Modification du profil du coach connecté (US09).
 *
 * <p>L'e-mail est facultatif : absent ou {@code null}, il est inchangé. Le prénom et le nom
 * sont obligatoires. Il n'y a pas de champ {@code role} : le rôle est fixé à la création et n'est
 * pas modifiable depuis l'interface (RM3).
 */
public record UpdateProfileRequest(
        @Email(message = "L'e-mail n'est pas valide")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName) {

    public UpdateProfileRequest {
        email = email == null ? null : EmailNormalizer.normalize(email);
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
    }
}
