package com.athletiq.planning;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * « Aujourd'hui » pour la planification (RM6, Q12) : le jour courant dans le fuseau de référence, à partir
 * du {@code Clock} injectable (UTC). Les dates de séance ne passent jamais par un {@code Instant}.
 */
@Component
@RequiredArgsConstructor
public class PlanningClock {

    private final Clock clock;
    private final PlanningProperties properties;

    public LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }
}
