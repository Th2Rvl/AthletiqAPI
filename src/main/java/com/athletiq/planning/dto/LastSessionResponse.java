package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dernière séance réalisée (4 dernières semaines) : RPE ({@code null} = non renseigné), séries faites sur
 * séries prévues, et présence d'un commentaire pour le coach.
 */
public record LastSessionResponse(
        UUID id,
        LocalDate date,
        String name,
        Integer rpe,
        int doneSetCount,
        long setCount,
        boolean hasComment) {
}
