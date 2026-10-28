package com.athletiq.performance;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.InvalidFieldException;

/** Codes et messages d'erreur de la saisie du réalisé. Le front s'appuie sur les codes. */
public final class PerformanceErrors {

    public static final String PERFORMED_SET_UNKNOWN = "PERFORMED_SET_UNKNOWN";
    public static final String PERFORMED_SET_DUPLICATE = "PERFORMED_SET_DUPLICATE";
    public static final String PERFORMANCE_EMPTY = "PERFORMANCE_EMPTY";
    public static final String PERFORMANCE_CONFLICT = "PERFORMANCE_CONFLICT";

    private PerformanceErrors() {
    }

    /** Une série envoyée n'appartient pas à cette séance : 400, rattaché au champ {@code sets}. */
    public static InvalidFieldException performedSetUnknown() {
        return new InvalidFieldException(PERFORMED_SET_UNKNOWN, "Une série ne fait pas partie de cette séance", "sets");
    }

    public static InvalidFieldException performedSetDuplicate() {
        return new InvalidFieldException(PERFORMED_SET_DUPLICATE, "Une série est envoyée deux fois", "sets");
    }

    /** Valider une séance sans aucune série faite : elle serait comptée réalisée sans l'avoir été. */
    public static BusinessRuleException performanceEmpty() {
        return new BusinessRuleException(PERFORMANCE_EMPTY, "Renseignez au moins une série faite pour valider la séance",
                "sets");
    }

    /** Deux enregistrements simultanés de la même séance : le second doit relire puis réessayer. */
    public static BusinessRuleException performanceConflict() {
        return new BusinessRuleException(PERFORMANCE_CONFLICT, "La séance vient d'être modifiée, réessayez");
    }
}
