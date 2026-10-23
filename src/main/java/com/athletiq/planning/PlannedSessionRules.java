package com.athletiq.planning;

import java.time.LocalDate;

/**
 * Verrouillage d'une séance planifiée (RM7, Q6) : passée (date antérieure à aujourd'hui) ou réalisée.
 * Point d'extension de #10 : y ajouter « réalisée » quand la réalisation existera.
 */
public final class PlannedSessionRules {

    private PlannedSessionRules() {
    }

    public static boolean isLocked(PlannedSession plannedSession, LocalDate today) {
        return isLocked(plannedSession.getScheduledDate(), today);
    }

    /** Variante pour les lignes de calendrier, qui ne chargent pas l'entité. */
    public static boolean isLocked(LocalDate scheduledDate, LocalDate today) {
        return scheduledDate.isBefore(today);
    }
}
