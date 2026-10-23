package com.athletiq.exercise.dto;

import com.athletiq.exercise.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Remplacement complet d'un exercice (PUT) : mêmes champs et mêmes règles que la création. Le constructeur compact retire les espaces de bord avant Bean Validation,
 * comme pour les groupes : un nom fait d'espaces est refusé.
 */
public record UpdateExerciseRequest(
        @NotBlank(message = "Le nom de l'exercice est obligatoire")
        @Size(max = 200, message = "Le nom ne doit pas dépasser 200 caractères")
        String name,

        @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
        String description,

        @NotNull(message = "Le groupe musculaire est obligatoire")
        MuscleGroup muscleGroup,

        @Size(max = 100, message = "Le matériel ne doit pas dépasser 100 caractères")
        String equipment,

        @Size(max = 500, message = "Le lien vidéo ne doit pas dépasser 500 caractères")
        @URL(message = "Le lien vidéo doit être une URL valide")
        String videoUrl) {

    public UpdateExerciseRequest {
        name = name == null ? null : name.strip();
        description = blankToNull(description);
        equipment = blankToNull(equipment);
        videoUrl = blankToNull(videoUrl);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
