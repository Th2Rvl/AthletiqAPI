package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Séances de l'athlète connecté sur {@code from}..{@code to} (inclus). {@code today} est aujourd'hui dans le
 * fuseau de référence : le mobile ne le calcule jamais. Plusieurs séances le même jour sont possibles.
 */
public record AthleteSessionListResponse(
        LocalDate from,
        LocalDate to,
        LocalDate today,
        List<AthleteSessionSummary> sessions) {
}
