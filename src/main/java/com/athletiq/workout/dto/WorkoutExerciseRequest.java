package com.athletiq.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Exercice de séance. Sa position est son index dans la liste {@code exercises}. Le même
 * {@code exerciseId} peut apparaître plusieurs fois dans une séance (Q11).
 */
public record WorkoutExerciseRequest(
        @NotNull(message = "L'exercice est obligatoire")
        UUID exerciseId,

        @Size(max = 500, message = "La note ne doit pas dépasser 500 caractères")
        String note,

        @NotEmpty(message = "Ajoutez au moins une série")
        @Size(max = 20, message = "Un exercice compte au plus 20 séries")
        List<@NotNull(message = "Série manquante") @Valid PlannedSetRequest> sets) {

    public WorkoutExerciseRequest {
        note = WorkoutRequest.blankToNull(note);
    }
}
