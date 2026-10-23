package com.athletiq.group.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Ajout d'un lot d'athlètes à un groupe. Les doublons de la liste sont ignorés. */
public record AddGroupMembersRequest(
        @NotEmpty(message = "Choisissez au moins un athlète")
        @Size(max = 100, message = "Pas plus de 100 athlètes à la fois")
        List<@NotNull(message = "Identifiant d'athlète manquant") UUID> athleteIds) {
}
