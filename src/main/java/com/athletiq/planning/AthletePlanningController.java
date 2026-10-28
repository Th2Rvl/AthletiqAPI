package com.athletiq.planning;

import com.athletiq.planning.dto.AthleteSessionDetailResponse;
import com.athletiq.planning.dto.AthleteSessionListResponse;
import com.athletiq.planning.dto.AthleteTodayResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Séances planifiées de l'athlète connecté, en lecture seule (#9, utilisée par l'app mobile). */
@RestController
@RequestMapping("/api/v1/me/planned-sessions")
@PreAuthorize("hasRole('ATHLETE')")
@RequiredArgsConstructor
public class AthletePlanningController {

    private final AthletePlanningService athletePlanningService;

    @GetMapping("/today")
    public AthleteTodayResponse today() {
        return athletePlanningService.today();
    }

    @GetMapping
    public AthleteSessionListResponse calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return athletePlanningService.calendar(from, to);
    }

    @GetMapping("/{plannedSessionId}")
    public AthleteSessionDetailResponse get(@PathVariable UUID plannedSessionId) {
        return athletePlanningService.get(plannedSessionId);
    }
}
