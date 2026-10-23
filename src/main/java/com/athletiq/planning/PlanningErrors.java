package com.athletiq.planning;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.ResourceNotFoundException;

/** Codes et messages d'erreur de la planification. Le front s'appuie sur les codes. */
public final class PlanningErrors {

    public static final String PLANNED_SESSION_NOT_FOUND = "PLANNED_SESSION_NOT_FOUND";
    public static final String PLANNING_DATE_IN_PAST = "PLANNING_DATE_IN_PAST";
    public static final String PLANNED_SESSION_LOCKED = "PLANNED_SESSION_LOCKED";
    public static final String GROUP_HAS_NO_MEMBERS = "GROUP_HAS_NO_MEMBERS";
    public static final String PLANNING_WEEK_EMPTY = "PLANNING_WEEK_EMPTY";
    public static final String PLANNING_BATCH_TOO_LARGE = "PLANNING_BATCH_TOO_LARGE";

    private static final String DATE_IN_PAST = "Impossible de planifier à une date passée";

    private PlanningErrors() {
    }

    /** Inexistante, d'un autre coach ou d'un athlète au lien clos : 404, jamais 403 (RM1). */
    public static ResourceNotFoundException plannedSessionNotFound() {
        return new ResourceNotFoundException(PLANNED_SESSION_NOT_FOUND, "Cette séance planifiée n'existe plus");
    }

    /** Création ou déplacement vers un jour passé (Q5), rattaché au champ {@code date}. */
    public static BusinessRuleException dateInPast() {
        return new BusinessRuleException(PLANNING_DATE_IN_PAST, DATE_IN_PAST, "date");
    }

    /** Duplication dont une date cible est passée : aucun champ saisi ne porte cette date. */
    public static BusinessRuleException targetDateInPast() {
        return new BusinessRuleException(PLANNING_DATE_IN_PAST, DATE_IN_PAST);
    }

    /** Séance passée ou réalisée (Q6) : ni déplacement ni annulation. */
    public static BusinessRuleException plannedSessionLocked() {
        return new BusinessRuleException(PLANNED_SESSION_LOCKED,
                "Cette séance est passée ou réalisée : elle ne peut plus être modifiée");
    }

    public static BusinessRuleException groupHasNoMembers() {
        return new BusinessRuleException(GROUP_HAS_NO_MEMBERS, "Ce groupe n'a aucun athlète");
    }

    public static BusinessRuleException weekEmpty() {
        return new BusinessRuleException(PLANNING_WEEK_EMPTY, "Aucune séance à dupliquer cette semaine");
    }

    public static BusinessRuleException batchTooLarge() {
        return new BusinessRuleException(PLANNING_BATCH_TOO_LARGE, "Trop de séances à créer d'un coup");
    }
}
