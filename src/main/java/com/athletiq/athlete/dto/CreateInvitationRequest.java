package com.athletiq.athlete.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création d'une invitation : le libellé aide le coach à reconnaître l'athlète attendu. */
public record CreateInvitationRequest(
        @NotBlank(message = "Le libellé est obligatoire")
        @Size(max = 100, message = "Le libellé ne doit pas dépasser 100 caractères")
        String label) {

    public CreateInvitationRequest {
        label = label == null ? null : label.strip();
    }
}
