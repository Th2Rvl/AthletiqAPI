package com.athletiq.performance;

import com.athletiq.performance.dto.AthleteKpisResponse;
import com.athletiq.performance.dto.PerformedExerciseResponse;
import com.athletiq.performance.dto.PersonalRecordsResponse;
import com.athletiq.performance.dto.ProgressionResponse;
import com.athletiq.performance.dto.SessionHistoryFilter;
import com.athletiq.performance.dto.SessionHistoryItem;
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
 * Suivi de l'athlète connecté, en lecture seule (#11, utilisée par l'app mobile) : historique, records,
 * progression et assiduité. Dates en {@code AAAA-MM-JJ} ; un paramètre mal formé ou inconnu répond 400.
 */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('ATHLETE')")
@RequiredArgsConstructor
public class AthleteTrackingController {

    private final AthleteTrackingService service;

    /** Tri fixe (date, création, id, du plus récent au plus ancien), 20 par page par défaut, 100 au plus. */
    @GetMapping("/session-history")
    public PagedModel<SessionHistoryItem> sessionHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "false") boolean withComment,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(service.sessionHistory(new SessionHistoryFilter(from, to, status, withComment),
                PageRequest.of(page, size)));
    }

    @GetMapping("/personal-records")
    public PersonalRecordsResponse personalRecords() {
        return service.personalRecords();
    }

    @GetMapping("/performed-exercises")
    public List<PerformedExerciseResponse> performedExercises() {
        return service.performedExercises();
    }

    /** {@code weeks} : 4, 12 (par défaut), 26 ou 52. */
    @GetMapping("/exercises/{exerciseId}/progression")
    public ProgressionResponse progression(@PathVariable UUID exerciseId,
                                           @RequestParam(defaultValue = "12") int weeks) {
        return service.progression(exerciseId, weeks);
    }

    /** {@code weeks} : 4 (par défaut) ou 12. */
    @GetMapping("/kpis")
    public AthleteKpisResponse kpis(@RequestParam(defaultValue = "4") int weeks) {
        return service.kpis(weeks);
    }
}
