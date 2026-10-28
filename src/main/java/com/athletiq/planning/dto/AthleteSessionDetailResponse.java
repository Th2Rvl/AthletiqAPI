package com.athletiq.planning.dto;

import com.athletiq.planning.SessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Détail d'une séance de l'athlète : le prévu, tel que le coach l'a planifié (instantané). */
public record AthleteSessionDetailResponse(
        UUID id,
        LocalDate date,
        LocalDate today,
        String name,
        String description,
        UUID coachId,
        String coachFirstName,
        String coachLastName,
        SessionStatus status,
        List<AthleteSessionExercise> exercises) {
}
