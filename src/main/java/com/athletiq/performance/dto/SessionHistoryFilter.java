package com.athletiq.performance.dto;

import com.athletiq.planning.SessionStatus;
import java.time.LocalDate;

/**
 * Filtres de l'historique, tels que reçus en paramètres (tous facultatifs) : période {@code from}..{@code to}
 * (par défaut les 4 semaines jusqu'à aujourd'hui), statut {@code DONE} ou {@code MISSED}, et
 * {@code withComment} pour ne garder que les séances réalisées commentées. Validés par le service.
 */
public record SessionHistoryFilter(
        LocalDate from,
        LocalDate to,
        SessionStatus status,
        boolean withComment) {
}
