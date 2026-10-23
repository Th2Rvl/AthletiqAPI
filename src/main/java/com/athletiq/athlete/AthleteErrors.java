package com.athletiq.athlete;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.InvalidFieldException;
import com.athletiq.common.exception.ResourceNotFoundException;

/**
 * Codes et messages d'erreur du suivi des athlètes et des invitations. Le front s'appuie sur les
 * codes : ils ne changent pas quand le libellé s'améliore.
 */
public final class AthleteErrors {

    public static final String INVITATION_CODE_INVALID = "INVITATION_CODE_INVALID";
    public static final String ATHLETE_ALREADY_LINKED = "ATHLETE_ALREADY_LINKED";
    public static final String INVITATION_NOT_FOUND = "INVITATION_NOT_FOUND";
    public static final String ATHLETE_NOT_FOUND = "ATHLETE_NOT_FOUND";

    private AthleteErrors() {
    }

    /** Inconnu, expiré, révoqué, déjà utilisé ou mal formé : un seul code, pour ne rien révéler (RM3). */
    public static InvalidFieldException invitationCodeInvalid() {
        return new InvalidFieldException(INVITATION_CODE_INVALID, "Ce code n'est pas valable ou a expiré", "code");
    }

    public static BusinessRuleException athleteAlreadyLinked() {
        return new BusinessRuleException(ATHLETE_ALREADY_LINKED, "Vous êtes déjà suivi par ce coach");
    }

    public static ResourceNotFoundException invitationNotFound() {
        return new ResourceNotFoundException(INVITATION_NOT_FOUND, "Cette invitation n'existe plus");
    }

    /** Athlète non suivi, déjà retiré ou suivi par un autre coach : 404, sans dire lequel (RM4). */
    public static ResourceNotFoundException athleteNotFound() {
        return new ResourceNotFoundException(ATHLETE_NOT_FOUND, "Cet athlète n'est plus suivi");
    }
}
