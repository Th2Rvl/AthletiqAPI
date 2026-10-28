package com.athletiq.performance;

import com.athletiq.performance.dto.PerformedExerciseResponse;
import com.athletiq.performance.dto.PersonalRecordsResponse;
import com.athletiq.performance.dto.ProgressionResponse;
import com.athletiq.performance.dto.SessionHistoryFilter;
import com.athletiq.performance.dto.SessionHistoryItem;
import com.athletiq.performance.dto.SessionReviewResponse;
import com.athletiq.performance.dto.TrackingOverviewResponse;
import com.athletiq.planning.SessionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suivi des athlètes par leur coach (US27 à US31), en lecture seule et au coach uniquement (RM2). Dates en
 * {@code AAAA-MM-JJ} ; un paramètre mal formé ou une valeur inconnue répond 400 {@code INVALID_PARAMETER}.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class PerformanceController {

    static final String ATHLETE = "/athletes/{athleteId}";

    private final PerformanceService performanceService;

    @GetMapping(ATHLETE + "/tracking")
    public TrackingOverviewResponse overview(@PathVariable UUID athleteId) {
        return performanceService.overview(athleteId);
    }

    /** Tri fixe (date, création, id, du plus récent au plus ancien), 20 par page par défaut, 100 au plus. */
    @GetMapping(ATHLETE + "/session-history")
    public PagedModel<SessionHistoryItem> sessionHistory(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "false") boolean withComment,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(performanceService.sessionHistory(athleteId,
                new SessionHistoryFilter(from, to, status, withComment), PageRequest.of(page, size)));
    }

    @GetMapping("/planned-sessions/{plannedSessionId}/review")
    public SessionReviewResponse review(@PathVariable UUID plannedSessionId) {
        return performanceService.review(plannedSessionId);
    }

    /** Non paginée : bornée par les exercices de la bibliothèque du coach. */
    @GetMapping(ATHLETE + "/performed-exercises")
    public List<PerformedExerciseResponse> performedExercises(@PathVariable UUID athleteId) {
        return performanceService.performedExercises(athleteId);
    }

    /** {@code weeks} : 4, 12 (par défaut), 26 ou 52. */
    @GetMapping(ATHLETE + "/exercises/{exerciseId}/progression")
    public ProgressionResponse progression(@PathVariable UUID athleteId, @PathVariable UUID exerciseId,
                                           @RequestParam(defaultValue = "12") int weeks) {
        return performanceService.progression(athleteId, exerciseId, weeks);
    }

    @GetMapping(ATHLETE + "/personal-records")
    public PersonalRecordsResponse personalRecords(@PathVariable UUID athleteId) {
        return performanceService.personalRecords(athleteId);
    }
}
