package com.athletiq.organisation;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.ResourceNotFoundException;

/** Codes et messages d'erreur de la fonctionnalité organisation. Le front s'appuie sur les codes. */
final class OrganisationErrors {

    static final String ORGANISATION_NOT_FOUND = "ORGANISATION_NOT_FOUND";
    static final String COACH_NOT_MEMBER = "COACH_NOT_MEMBER";
    static final String INVITATION_NOT_FOUND = "INVITATION_NOT_FOUND";
    static final String ORGANISATION_NAME_TAKEN = "ORGANISATION_NAME_TAKEN";
    static final String COACH_ALREADY_MEMBER = "COACH_ALREADY_MEMBER";
    static final String INVITATION_ALREADY_PENDING = "INVITATION_ALREADY_PENDING";
    static final String ACCOUNT_NOT_COACH = "ACCOUNT_NOT_COACH";
    static final String INVITATION_NOT_PENDING = "INVITATION_NOT_PENDING";

    private OrganisationErrors() {
    }

    static ResourceNotFoundException organisationNotFound() {
        return new ResourceNotFoundException(ORGANISATION_NOT_FOUND, "Cette organisation n'existe pas");
    }

    static ResourceNotFoundException coachNotMember() {
        return new ResourceNotFoundException(COACH_NOT_MEMBER, "Ce coach ne fait pas partie de l'organisation");
    }

    static ResourceNotFoundException invitationNotFound() {
        return new ResourceNotFoundException(INVITATION_NOT_FOUND, "Cette invitation n'existe pas");
    }

    static BusinessRuleException nameTaken() {
        return new BusinessRuleException(ORGANISATION_NAME_TAKEN, "Une organisation porte déjà ce nom", "name");
    }

    static BusinessRuleException coachAlreadyMember() {
        return new BusinessRuleException(COACH_ALREADY_MEMBER, "Ce coach fait déjà partie de l'organisation",
                "email");
    }

    static BusinessRuleException invitationAlreadyPending() {
        return new BusinessRuleException(INVITATION_ALREADY_PENDING,
                "Une invitation est déjà en attente pour cet e-mail", "email");
    }

    static BusinessRuleException accountNotCoach() {
        return new BusinessRuleException(ACCOUNT_NOT_COACH, "Ce compte n'est pas un compte coach", "email");
    }

    static BusinessRuleException invitationNotPending() {
        return new BusinessRuleException(INVITATION_NOT_PENDING, "Cette invitation n'est plus en attente");
    }
}
