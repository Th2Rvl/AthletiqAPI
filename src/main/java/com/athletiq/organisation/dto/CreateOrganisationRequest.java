package com.athletiq.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Création d'une organisation. Le constructeur compact normalise les valeurs avant Bean Validation :
 * espaces de début et de fin retirés, description vide ramenée à {@code null}.
 */
public record CreateOrganisationRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
        String name,

        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
        String description) {

    public CreateOrganisationRequest {
        name = OrganisationInputs.normalizeName(name);
        description = OrganisationInputs.normalizeDescription(description);
    }
}
