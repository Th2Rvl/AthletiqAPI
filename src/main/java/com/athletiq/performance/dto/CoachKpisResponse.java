package com.athletiq.performance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tableau de bord du coach (US32) sur {@code weeks} semaines glissantes, aujourd'hui exclu :
 * {@code from = today - weeks × 7}, {@code to = today - 1}. Seules ses séances planifiées comptent (RM1), et
 * seuls ses athlètes suivis apparaissent, membres de {@code groupId} s'il est donné, y compris sans séance.
 * {@code athletes} est trié par nom puis prénom ; {@code watchlist} par non faites (décroissant), non faites
 * récentes (décroissant), puis nom.
 */
public record CoachKpisResponse(
        LocalDate today,
        LocalDate from,
        LocalDate to,
        int weeks,
        UUID groupId,
        KpiTotals totals,
        List<AthleteKpi> athletes,
        List<WatchlistEntry> watchlist) {
}
