package com.athletiq.exercise;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.ResourceNotFoundException;
import java.util.Map;

/**
 * Codes et messages d'erreur de la bibliothèque d'exercices. Le front s'appuie sur les codes.
 */
public final class ExerciseErrors {

    public static final String EXERCISE_NOT_FOUND = "EXERCISE_NOT_FOUND";
    public static final String EXERCISE_NAME_ALREADY_EXISTS = "EXERCISE_NAME_ALREADY_EXISTS";
    public static final String EXERCISE_IN_USE = "EXERCISE_IN_USE";
    /** Propriété du ProblemDetail de {@code EXERCISE_IN_USE} : nombre de séances qui utilisent l'exercice. */
    public static final String WORKOUT_COUNT = "workoutCount";
    /** Propriété du ProblemDetail de {@code EXERCISE_IN_USE} : nombre de séances planifiées concernées (Q11 de #6). */
    public static final String PLANNED_SESSION_COUNT = "plannedSessionCount";

    private ExerciseErrors() {
    }

    /** Exercice inexistant ou appartenant à un autre coach : 404, sans dire lequel (RM4). */
    public static ResourceNotFoundException exerciseNotFound() {
        return new ResourceNotFoundException(EXERCISE_NOT_FOUND, "Cet exercice n'existe plus");
    }

    public static BusinessRuleException exerciseNameAlreadyExists() {
        return new BusinessRuleException(EXERCISE_NAME_ALREADY_EXISTS,
                "Vous avez déjà un exercice de ce nom dans votre bibliothèque", "name");
    }

    /**
     * Exercice présent dans au moins une séance (RM9) ou une séance planifiée (Q11 de #6) : 409, avec les
     * deux compteurs toujours présents. Le {@code detail} omet la partie à 0 : « … dans 2 séances et
     * 3 séances planifiées », « … dans 1 séance planifiée ».
     */
    public static BusinessRuleException exerciseInUse(long workoutCount, long plannedSessionCount) {
        StringBuilder detail = new StringBuilder("Cet exercice est utilisé dans ");
        if (workoutCount > 0) {
            detail.append(workoutCount).append(workoutCount > 1 ? " séances" : " séance");
        }
        if (workoutCount > 0 && plannedSessionCount > 0) {
            detail.append(" et ");
        }
        if (plannedSessionCount > 0) {
            detail.append(plannedSessionCount)
                    .append(plannedSessionCount > 1 ? " séances planifiées" : " séance planifiée");
        }
        return new BusinessRuleException(EXERCISE_IN_USE, detail.toString(), null,
                Map.of(WORKOUT_COUNT, workoutCount, PLANNED_SESSION_COUNT, plannedSessionCount));
    }
}
