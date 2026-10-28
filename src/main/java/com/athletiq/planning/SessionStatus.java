package com.athletiq.planning;

import java.time.LocalDate;

/**
 * Statut d'une séance planifiée, calculé à la lecture et jamais stocké (#7, RM3) :
 * <ul>
 *   <li>{@code DONE} : sa réalisation est validée par l'athlète, quelle que soit la date de validation
 *       (Q17 : une séance validée avant sa date est déjà réalisée) ;</li>
 *   <li>{@code MISSED} : sa date est passée sans réalisation validée (un brouillon ne compte pas) ;</li>
 *   <li>{@code UPCOMING} : sinon (aujourd'hui compris).</li>
 * </ul>
 * Vit dans {@code planning} : le calendrier l'affiche, et {@code performance} dépend de {@code planning},
 * jamais l'inverse.
 */
public enum SessionStatus {
    UPCOMING,
    DONE,
    MISSED;

    /** {@code done} : la séance a une réalisation validée. {@code today} : aujourd'hui à Paris (Q12 de #6). */
    public static SessionStatus of(LocalDate scheduledDate, LocalDate today, boolean done) {
        if (done) {
            return DONE;
        }
        return scheduledDate.isBefore(today) ? MISSED : UPCOMING;
    }
}
