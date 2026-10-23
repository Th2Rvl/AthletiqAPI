package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calendrier d'un athlète sur la période {@code from}..{@code to} (inclusifs), limité aux séances du coach
 * connecté. {@code today} est aujourd'hui dans le fuseau de référence : le front ne le calcule jamais.
 * {@code sessions} est trié par date, création, id, et peut contenir plusieurs séances le même jour.
 */
public record AthleteCalendarResponse(
        UUID athleteId,
        LocalDate from,
        LocalDate to,
        LocalDate today,
        List<PlannedSessionSummary> sessions) {
}
