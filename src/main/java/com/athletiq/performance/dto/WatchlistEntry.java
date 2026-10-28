package com.athletiq.performance.dto;

import java.util.List;
import java.util.UUID;

/** Athlète « À surveiller » (US32), avec ses raisons ; le front ouvre son suivi par {@code athleteId}. */
public record WatchlistEntry(
        UUID athleteId,
        String firstName,
        String lastName,
        int missedCount,
        int missedLast7Days,
        Integer completionRate,
        List<WatchReason> reasons) {
}
