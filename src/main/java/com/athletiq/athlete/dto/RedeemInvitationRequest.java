package com.athletiq.athlete.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Saisie d'un code par un athlète. La forme exacte (tirets, casse) est normalisée par le service. */
public record RedeemInvitationRequest(
        @NotBlank(message = "Le code est obligatoire")
        @Size(max = 32, message = "Le code ne doit pas dépasser 32 caractères")
        String code) {
}
