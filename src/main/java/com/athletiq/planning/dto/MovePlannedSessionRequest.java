package com.athletiq.planning.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Déplacer une séance planifiée (US25) : seule la date change, jamais le contenu (Q13). */
public record MovePlannedSessionRequest(
        @NotNull(message = "La date est obligatoire")
        LocalDate date) {
}
