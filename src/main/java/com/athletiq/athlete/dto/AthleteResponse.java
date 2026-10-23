package com.athletiq.athlete.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Athlète suivi par le coach connecté. {@code id} est celui du compte athlète, {@code joinedAt} la
 * date de rattachement et {@code groups} ses groupes chez ce coach uniquement. Aucune donnée
 * sensible ni aucun autre coach de l'athlète.
 */
public record AthleteResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        Instant joinedAt,
        List<GroupRef> groups) {
}
