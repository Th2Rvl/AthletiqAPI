package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Calendrier d'un groupe : {@code entries} triées par date, nom, instantané. */
public record GroupCalendarResponse(
        UUID groupId,
        LocalDate from,
        LocalDate to,
        LocalDate today,
        int memberCount,
        List<GroupCalendarEntry> entries) {
}
