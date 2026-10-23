package com.athletiq.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Remplacement complet d'une organisation : une description absente ou {@code null} est effacée.
 * Mêmes règles et même normalisation qu'à la création.
 */
public record UpdateOrganisationRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
        String name,

        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
        String description) {

    public UpdateOrganisationRequest {
        name = OrganisationInputs.normalizeName(name);
        description = OrganisationInputs.normalizeDescription(description);
    }
}
