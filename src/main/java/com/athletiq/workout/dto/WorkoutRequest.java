package com.athletiq.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Création et remplacement complet (PUT) d'une séance : l'ordre des exercices, et celui des séries de
 * chacun, est celui des listes (Q9). Le constructeur compact retire les espaces de bord avant Bean
 * Validation : un nom fait d'espaces est refusé.
 */
public record WorkoutRequest(
        @NotBlank(message = "Le nom de la séance est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name,

        @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
        String description,

        @NotEmpty(message = "Ajoutez au moins un exercice")
        @Size(max = 30, message = "Une séance compte au plus 30 exercices")
        List<@NotNull(message = "Exercice manquant") @Valid WorkoutExerciseRequest> exercises) {

    public WorkoutRequest {
        name = name == null ? null : name.strip();
        description = blankToNull(description);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
