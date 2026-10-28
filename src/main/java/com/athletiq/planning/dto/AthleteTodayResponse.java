package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Accueil de l'athlète : les séances d'aujourd'hui en détail (zéro, une ou plusieurs), la prochaine séance
 * après aujourd'hui et la dernière séance réalisée. {@code today} est calculé par l'API.
 */
public record AthleteTodayResponse(
        LocalDate today,
        List<AthleteSessionDetailResponse> sessions,
        UpcomingSessionResponse next,
        LastSessionResponse last) {
}
