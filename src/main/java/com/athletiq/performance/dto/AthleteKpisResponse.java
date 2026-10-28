package com.athletiq.performance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Assiduité de l'athlète connecté sur les {@code weeks} semaines qui finissent la veille de {@code today}
 * (aujourd'hui exclu). {@code completionRate} : entier, réalisées / (réalisées + non faites), {@code null} sans
 * séance passée. {@code averageRpe} : une décimale sur les séances réalisées avec RPE, {@code null} sinon.
 * {@code recentMissedCount} : non faites des {@code recentDays} derniers jours.
 */
public record AthleteKpisResponse(
        LocalDate today,
        LocalDate from,
        LocalDate to,
        int weeks,
        int doneCount,
        int missedCount,
        Integer completionRate,
        BigDecimal averageRpe,
        int recentDays,
        int recentMissedCount) {
}
