package com.athletiq.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renommage d'un groupe : mêmes règles que la création. */
public record RenameGroupRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 60, message = "Le nom ne doit pas dépasser 60 caractères")
        String name) {

    public RenameGroupRequest {
        name = name == null ? null : name.strip();
    }
}
