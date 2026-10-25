package com.athletiq.performance.dto;

import java.time.Instant;

/**
 * Ressenti d'une séance réalisée (US29), en lecture seule pour le coach : RPE 1..10, fatigue 1..5 et
 * commentaire, chacun {@code null} s'il n'est pas renseigné (« Non renseigné », jamais 0). {@code validatedAt}
 * est la première validation, {@code correctedAt} la dernière correction (« Modifiée le … »).
 */
public record SessionFeedback(
        Integer rpe,
        Integer fatigue,
        String comment,
        Instant validatedAt,
        Instant correctedAt) {
}
