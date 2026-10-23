package com.athletiq.planning;

import com.athletiq.planning.dto.AthleteCalendarResponse;
import com.athletiq.planning.dto.CreatePlannedSessionRequest;
import com.athletiq.planning.dto.CreatedCountResponse;
import com.athletiq.planning.dto.DuplicateWeekRequest;
import com.athletiq.planning.dto.GroupCalendarResponse;
import com.athletiq.planning.dto.MovePlannedSessionRequest;
import com.athletiq.planning.dto.PlannedSessionDetailResponse;
import com.athletiq.planning.dto.UpcomingCountResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Planification des séances (US21-US26), coach uniquement (RM12). Les calendriers ne sont pas paginés :
 * la période, bornée à 12 semaines (RM13), borne déjà la liste (exception documentée à la règle de
 * pagination). Dates en {@code AAAA-MM-JJ} : un format invalide répond 400 {@code INVALID_PARAMETER} en
 * paramètre, {@code MALFORMED_REQUEST} dans un corps.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class PlanningController {

    static final String PLANNED_SESSIONS = "/planned-sessions";
    static final String ATHLETE_SESSIONS = "/athletes/{athleteId}" + PLANNED_SESSIONS;
    static final String GROUP_SESSIONS = "/groups/{groupId}" + PLANNED_SESSIONS;
    static final String ONE = PLANNED_SESSIONS + "/{plannedSessionId}";

    private final PlanningService planningService;

    // ---------------------------------------------------------------- athlète

    @GetMapping(ATHLETE_SESSIONS)
    public AthleteCalendarResponse athleteCalendar(
            @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return planningService.athleteCalendar(athleteId, from, to);
    }

    @PostMapping(ATHLETE_SESSIONS)
    public ResponseEntity<PlannedSessionDetailResponse> planForAthlete(
            @PathVariable UUID athleteId, @Valid @RequestBody CreatePlannedSessionRequest request) {
        PlannedSessionDetailResponse created = planningService.planForAthlete(athleteId, request);
        URI location = UriComponentsBuilder.fromPath("/api/v1" + ONE).buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping(ATHLETE_SESSIONS + "/upcoming-count")
    public UpcomingCountResponse upcomingCount(@PathVariable UUID athleteId) {
        return planningService.upcomingCount(athleteId);
    }

    @PostMapping(ATHLETE_SESSIONS + "/duplicate-week")
    public ResponseEntity<CreatedCountResponse> duplicateWeekForAthlete(
            @PathVariable UUID athleteId, @Valid @RequestBody DuplicateWeekRequest request) {
        return createdWithoutLocation(planningService.duplicateWeekForAthlete(athleteId, request));
    }

    // ---------------------------------------------------------------- groupe

    @GetMapping(GROUP_SESSIONS)
    public GroupCalendarResponse groupCalendar(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return planningService.groupCalendar(groupId, from, to);
    }

    /** Plusieurs ressources créées : 201 sans {@code Location}, avec leur nombre. */
    @PostMapping(GROUP_SESSIONS)
    public ResponseEntity<CreatedCountResponse> planForGroup(
            @PathVariable UUID groupId, @Valid @RequestBody CreatePlannedSessionRequest request) {
        return createdWithoutLocation(planningService.planForGroup(groupId, request));
    }

    @PostMapping(GROUP_SESSIONS + "/duplicate-week")
    public ResponseEntity<CreatedCountResponse> duplicateWeekForGroup(
            @PathVariable UUID groupId, @Valid @RequestBody DuplicateWeekRequest request) {
        return createdWithoutLocation(planningService.duplicateWeekForGroup(groupId, request));
    }

    // ---------------------------------------------------------------- séance planifiée

    @GetMapping(ONE)
    public PlannedSessionDetailResponse get(@PathVariable UUID plannedSessionId) {
        return planningService.get(plannedSessionId);
    }

    @PatchMapping(ONE)
    public PlannedSessionDetailResponse move(@PathVariable UUID plannedSessionId,
                                             @Valid @RequestBody MovePlannedSessionRequest request) {
        return planningService.move(plannedSessionId, request);
    }

    @DeleteMapping(ONE)
    public ResponseEntity<Void> cancel(@PathVariable UUID plannedSessionId) {
        planningService.cancel(plannedSessionId);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<CreatedCountResponse> createdWithoutLocation(CreatedCountResponse body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
