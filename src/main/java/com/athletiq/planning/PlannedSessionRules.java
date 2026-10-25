package com.athletiq.planning;

import java.time.LocalDate;

/**
 * Verrouillage d'une séance planifiée (RM7 et Q6 de #6, RM10 de #7) : passée (date antérieure à aujourd'hui)
 * ou réalisée (réalisation validée, même si sa date est à venir). Une séance verrouillée ne se déplace ni ne
 * s'annule ({@code 409 PLANNED_SESSION_LOCKED}). Équivaut à un statut autre que {@link SessionStatus#UPCOMING}.
 */
public final class PlannedSessionRules {

    private PlannedSessionRules() {
    }

    /** {@code done} : la séance a une réalisation validée ({@link PlannedSessionCompletion}). */
    public static boolean isLocked(LocalDate scheduledDate, LocalDate today, boolean done) {
        return scheduledDate.isBefore(today) || done;
    }
}
