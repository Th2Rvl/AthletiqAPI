package com.athletiq.planning.dto;

import com.athletiq.planning.SessionStatus;
import java.time.LocalDate;
import java.util.UUID;

/** Séance du calendrier de l'athlète : son contenu en deux compteurs, son coach et son statut. */
public record AthleteSessionSummary(
        UUID id,
        LocalDate date,
        String name,
        long exerciseCount,
        long setCount,
        UUID coachId,
        String coachFirstName,
        String coachLastName,
        SessionStatus status) {
}
