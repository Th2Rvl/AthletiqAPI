package com.athletiq.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Création d'un groupe. Le constructeur compact retire les espaces de bord avant Bean Validation :
 * un nom fait uniquement d'espaces devient vide et est refusé, et la longueur se mesure après trim.
 */
public record CreateGroupRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 60, message = "Le nom ne doit pas dépasser 60 caractères")
        String name) {

    public CreateGroupRequest {
        name = name == null ? null : name.strip();
    }
}
