package com.athletiq.exercise;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.ResourceNotFoundException;

/**
 * Codes et messages d'erreur de la bibliothèque d'exercices. Le front s'appuie sur les codes.
 */
public final class ExerciseErrors {

    public static final String EXERCISE_NOT_FOUND = "EXERCISE_NOT_FOUND";
    public static final String EXERCISE_NAME_ALREADY_EXISTS = "EXERCISE_NAME_ALREADY_EXISTS";

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
}
