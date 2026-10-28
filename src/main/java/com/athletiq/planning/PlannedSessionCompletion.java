package com.athletiq.planning;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Réalisations validées des séances planifiées (#7), pour le statut et le verrouillage du calendrier
 * (RM10 : une séance réalisée ne se déplace ni ne s'annule, même future). L'interface appartient à
 * {@code planning} et est implémentée par {@code performance}, comme {@code exercise.ExerciseUsage} :
 * {@code performance} dépend de {@code planning}, jamais l'inverse.
 */
public interface PlannedSessionCompletion {

    /**
     * Parmi ces séances planifiées, celles dont la réalisation est validée, en une requête (une liste
     * vide ne coûte aucune requête). Un brouillon ne compte pas.
     */
    Set<UUID> findValidated(Collection<UUID> plannedSessionIds);
}
