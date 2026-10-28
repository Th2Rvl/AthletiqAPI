package com.athletiq.performance.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * En-tête de la page « Suivi » d'un athlète. {@code today} est aujourd'hui à Paris : le front ne le calcule
 * jamais. {@code hasPastSessions} : au moins une séance de ce coach pour cet athlète entre dans l'historique
 * (réalisée, ou passée), toutes périodes confondues ; sinon le front affiche l'état vide et « Planifier ».
 */
public record TrackingOverviewResponse(
        UUID athleteId,
        String firstName,
        String lastName,
        LocalDate today,
        boolean hasPastSessions) {
}
