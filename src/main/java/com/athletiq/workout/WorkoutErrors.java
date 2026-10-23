package com.athletiq.workout;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.ResourceNotFoundException;

/** Codes et messages d'erreur des séances. Le front s'appuie sur les codes. */
public final class WorkoutErrors {

    public static final String WORKOUT_NOT_FOUND = "WORKOUT_NOT_FOUND";
    public static final String WORKOUT_NAME_ALREADY_EXISTS = "WORKOUT_NAME_ALREADY_EXISTS";

    private static final String NAME_TAKEN = "Vous avez déjà une séance de ce nom";

    private WorkoutErrors() {
    }

    /** Séance inexistante ou appartenant à un autre coach : 404, jamais 403 (RM1). */
    public static ResourceNotFoundException workoutNotFound() {
        return new ResourceNotFoundException(WORKOUT_NOT_FOUND, "Cette séance n'existe plus");
    }

    /** Nom déjà pris par une autre séance du coach (RM5), rattaché au champ {@code name}. */
    public static BusinessRuleException workoutNameAlreadyExists() {
        return new BusinessRuleException(WORKOUT_NAME_ALREADY_EXISTS, NAME_TAKEN, "name");
    }

    /** Course perdue sur le nom d'une copie : aucun champ n'a été saisi, donc pas de {@code field}. */
    public static BusinessRuleException copyNameAlreadyExists() {
        return new BusinessRuleException(WORKOUT_NAME_ALREADY_EXISTS, NAME_TAKEN);
    }
}
