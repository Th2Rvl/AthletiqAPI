package com.athletiq.planning;

import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.planning.dto.AthleteSessionDetailResponse;
import com.athletiq.planning.dto.AthleteSessionExercise;
import com.athletiq.planning.dto.AthleteSessionListResponse;
import com.athletiq.planning.dto.AthleteSessionSet;
import com.athletiq.planning.dto.AthleteSessionSummary;
import com.athletiq.user.User;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation du planning par l'athlète connecté (#9, US07 à US09) : séance du jour, calendrier et détail.
 * Un athlète ne lit que ses propres séances (l'identité vient du JWT) et seulement s'il a un coach (US06).
 */
@Service
@RequiredArgsConstructor
public class AthletePlanningService {

    private final PlannedSessionRepository plannedSessionRepository;
    private final SessionSnapshotRepository snapshotRepository;
    private final SessionSnapshotExerciseRepository snapshotExerciseRepository;
    private final PlannedSessionCompletion completion;
    private final PlanningClock planningClock;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;

    /** US07 : les séances d'aujourd'hui (zéro, une ou plusieurs). */
    @Transactional(readOnly = true)
    public AthleteSessionListResponse today() {
        LocalDate today = planningClock.today();
        return sessions(today, today);
    }

    /** US08 : les séances de {@code from} à {@code to} inclus. */
    @Transactional(readOnly = true)
    public AthleteSessionListResponse calendar(LocalDate from, LocalDate to) {
        PlanningService.assertPeriod(from, to);
        return sessions(from, to);
    }

    /** US09 : le détail d'une séance de l'athlète. Une séance d'un autre athlète est introuvable (404). */
    @Transactional(readOnly = true)
    public AthleteSessionDetailResponse get(UUID plannedSessionId) {
        accessGuard.assertAthleteWithCoach();
        PlannedSession session = plannedSessionRepository.findOwnById(plannedSessionId, currentUser.id())
                .orElseThrow(PlanningErrors::plannedSessionNotFound);
        UUID snapshotId = session.getSnapshot().getId();
        SessionSnapshot snapshot = snapshotRepository.findDetailById(snapshotId)
                .orElseThrow(PlanningErrors::plannedSessionNotFound);
        if (!snapshot.getExercises().isEmpty()) {
            snapshotExerciseRepository.fetchSetsOfSnapshot(snapshotId);
        }
        LocalDate today = planningClock.today();
        User coach = session.getCoach();
        List<AthleteSessionExercise> exercises = snapshot.getExercises().stream()
                .sorted(Comparator.comparingInt(SessionSnapshotExercise::getPosition))
                .map(exercise -> new AthleteSessionExercise(exercise.getId(), exercise.getExerciseId(),
                        exercise.getExerciseName(), exercise.getMuscleGroup(), exercise.getPosition(),
                        exercise.getNote(),
                        exercise.getSets().stream()
                                .sorted(Comparator.comparingInt(SessionSnapshotSet::getPosition))
                                .map(set -> new AthleteSessionSet(set.getId(), set.getPosition(), set.getReps(),
                                        set.getLoadKg(), set.getRestSeconds()))
                                .toList()))
                .toList();
        return new AthleteSessionDetailResponse(session.getId(), session.getScheduledDate(), today,
                snapshot.getName(), snapshot.getDescription(), coach.getId(), coach.getFirstName(),
                coach.getLastName(), SessionStatus.of(session.getScheduledDate(), today, isDone(session.getId())),
                exercises);
    }

    private AthleteSessionListResponse sessions(LocalDate from, LocalDate to) {
        accessGuard.assertAthleteWithCoach();
        LocalDate today = planningClock.today();
        List<AthleteSessionRow> rows = plannedSessionRepository.findOwnRows(currentUser.id(), from, to);
        Map<UUID, SnapshotCount> counts = countsOf(rows);
        Set<UUID> done = rows.isEmpty() ? Set.of()
                : completion.findValidated(rows.stream().map(AthleteSessionRow::id).toList());
        List<AthleteSessionSummary> sessions = rows.stream()
                .map(row -> {
                    SnapshotCount count = counts.get(row.snapshotId());
                    return new AthleteSessionSummary(row.id(), row.date(), row.name(),
                            count == null ? 0 : count.exerciseCount(), count == null ? 0 : count.setCount(),
                            row.coachId(), row.coachFirstName(), row.coachLastName(),
                            SessionStatus.of(row.date(), today, done.contains(row.id())));
                })
                .toList();
        return new AthleteSessionListResponse(from, to, today, sessions);
    }

    private boolean isDone(UUID plannedSessionId) {
        return completion.findValidated(List.of(plannedSessionId)).contains(plannedSessionId);
    }

    private Map<UUID, SnapshotCount> countsOf(List<AthleteSessionRow> rows) {
        Set<UUID> snapshotIds = rows.stream().map(AthleteSessionRow::snapshotId).collect(Collectors.toSet());
        if (snapshotIds.isEmpty()) {
            return Map.of();
        }
        return snapshotExerciseRepository.countBySnapshot(snapshotIds).stream()
                .collect(Collectors.toMap(SnapshotCount::snapshotId, Function.identity()));
    }
}
