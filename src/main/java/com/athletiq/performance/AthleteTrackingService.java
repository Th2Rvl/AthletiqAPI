package com.athletiq.performance;

import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.ExerciseErrors;
import com.athletiq.performance.dto.AthleteKpisResponse;
import com.athletiq.performance.dto.PerformedExerciseResponse;
import com.athletiq.performance.dto.PersonalRecord;
import com.athletiq.performance.dto.PersonalRecordsResponse;
import com.athletiq.performance.dto.ProgressionPoint;
import com.athletiq.performance.dto.ProgressionResponse;
import com.athletiq.performance.dto.SessionHistoryFilter;
import com.athletiq.performance.dto.SessionHistoryItem;
import com.athletiq.planning.PlanningClock;
import com.athletiq.planning.SessionStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historique, records, progression et assiduité de l'athlète connecté (#11, US15 à US18). Mêmes définitions
 * que le suivi du coach (séance réalisée = réalisation validée, série réalisée = répétitions renseignées), mais
 * sur toutes ses séances, tous coachs confondus : l'identité vient du JWT. Sans coach actif, rien n'est lu (US06).
 */
@Service
@RequiredArgsConstructor
public class AthleteTrackingService {

    private final AthleteTrackingRepository repository;
    private final PerformanceMapper mapper;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final PlanningClock planningClock;
    private final KpiProperties kpiProperties;

    /** US15 : séances réalisées et non faites, de la plus récente à la plus ancienne. */
    @Transactional(readOnly = true)
    public Page<SessionHistoryItem> sessionHistory(SessionHistoryFilter filter, Pageable pageable) {
        PerformanceService.HistoryCriteria criteria = PerformanceService.HistoryCriteria.of(filter, planningClock.today());
        accessGuard.assertAthleteWithCoach();
        LocalDate today = criteria.today();
        return repository.findHistory(currentUser.id(), criteria.from(), criteria.to(), today,
                        criteria.includeDone(), criteria.includeMissed(), criteria.withComment(),
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(row -> mapper.toHistoryItem(row, SessionStatus.of(row.date(), today, row.done())));
    }

    /** US16 : un record par exercice réalisé, toutes périodes, recalculé à chaque lecture. */
    @Transactional(readOnly = true)
    public PersonalRecordsResponse personalRecords() {
        accessGuard.assertAthleteWithCoach();
        UUID athleteId = currentUser.id();
        List<PersonalRecord> records = PerformanceService.byExercise(repository.findPerformedSetGroups(athleteId))
                .values().stream()
                .map(PerformanceService::personalRecord)
                .sorted(Comparator.comparing((PersonalRecord record) -> record.muscleGroup().name())
                        .thenComparing(PersonalRecord::exerciseName, PerformanceService.NAME_ORDER)
                        .thenComparing(PersonalRecord::exerciseId))
                .toList();
        return new PersonalRecordsResponse(athleteId, records);
    }

    /** US17 : exercices réalisés au moins une fois, pour choisir celui dont on suit la progression. */
    @Transactional(readOnly = true)
    public List<PerformedExerciseResponse> performedExercises() {
        accessGuard.assertAthleteWithCoach();
        return PerformanceService.byExercise(repository.findPerformedSetGroups(currentUser.id())).values().stream()
                .map(rows -> {
                    PerformedSetGroupRow latest = PerformanceService.latest(rows);
                    int sessionCount = (int) rows.stream().map(PerformedSetGroupRow::plannedSessionId).distinct().count();
                    return new PerformedExerciseResponse(latest.exerciseId(), latest.exerciseName(),
                            latest.muscleGroup(), sessionCount, latest.date());
                })
                .sorted(Comparator.comparing((PerformedExerciseResponse exercise) -> exercise.muscleGroup().name())
                        .thenComparing(PerformedExerciseResponse::exerciseName, PerformanceService.NAME_ORDER)
                        .thenComparing(PerformedExerciseResponse::exerciseId))
                .toList();
    }

    /**
     * US17 : un point par séance réalisée de l'exercice sur {@code weeks} semaines (4, 12, 26 ou 52, sinon 400).
     * Un exercice que l'athlète n'a jamais réalisé est introuvable (404 {@code EXERCISE_NOT_FOUND}).
     */
    @Transactional(readOnly = true)
    public ProgressionResponse progression(UUID exerciseId, int weeks) {
        if (!PerformanceService.PROGRESSION_WEEKS.contains(weeks)) {
            throw new InvalidParameterException("weeks");
        }
        accessGuard.assertAthleteWithCoach();
        UUID athleteId = currentUser.id();
        ExerciseLabel label = repository.findLatestLabels(athleteId, exerciseId, PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElseThrow(ExerciseErrors::exerciseNotFound);
        LocalDate today = planningClock.today();
        LocalDate from = today.minusWeeks(weeks);
        List<ProgressionPoint> points = repository.findProgressionRows(athleteId, exerciseId, from).stream()
                .map(row -> new ProgressionPoint(row.plannedSessionId(), row.date(),
                        PerformanceMath.kg(row.maxLoadKg()), row.maxReps(), PerformanceMath.kg(row.volumeKg())))
                .toList();
        boolean bodyweight = points.stream().allMatch(point -> point.maxLoadKg() == null);
        return new ProgressionResponse(athleteId, exerciseId, label.exerciseName(), label.muscleGroup(), bodyweight,
                weeks, from, today, points);
    }

    /** US18 : assiduité sur 4 ou 12 semaines glissantes, aujourd'hui exclu. */
    @Transactional(readOnly = true)
    public AthleteKpisResponse kpis(int weeks) {
        if (!KpiService.WEEKS.contains(weeks)) {
            throw new InvalidParameterException("weeks");
        }
        accessGuard.assertAthleteWithCoach();
        LocalDate today = planningClock.today();
        LocalDate from = today.minusWeeks(weeks);
        LocalDate to = today.minusDays(1);
        int recentDays = kpiProperties.missedDays();
        AthleteKpiRow row = repository.aggregateKpi(currentUser.id(), from, to, today.minusDays(recentDays)).orElse(null);
        int done = row == null ? 0 : Math.toIntExact(row.doneCount());
        int missed = row == null ? 0 : Math.toIntExact(row.missedCount());
        long rpeSum = row == null || row.rpeSum() == null ? 0 : row.rpeSum();
        return new AthleteKpisResponse(today, from, to, weeks, done, missed,
                PerformanceMath.completionRate(done, missed),
                PerformanceMath.average(rpeSum, row == null ? 0 : row.rpeCount()),
                recentDays, row == null ? 0 : Math.toIntExact(row.missedRecently()));
    }
}
