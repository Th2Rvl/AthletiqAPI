package com.athletiq.performance;

import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.ExerciseService;
import com.athletiq.exercise.dto.ExerciseResponse;
import com.athletiq.performance.dto.PerformedExerciseResponse;
import com.athletiq.performance.dto.PersonalRecord;
import com.athletiq.performance.dto.PersonalRecordsResponse;
import com.athletiq.performance.dto.ProgressionPoint;
import com.athletiq.performance.dto.ProgressionResponse;
import com.athletiq.performance.dto.ReviewExercise;
import com.athletiq.performance.dto.ReviewSet;
import com.athletiq.performance.dto.SessionHistoryFilter;
import com.athletiq.performance.dto.SessionHistoryItem;
import com.athletiq.performance.dto.SessionReviewResponse;
import com.athletiq.performance.dto.TrackingOverviewResponse;
import com.athletiq.planning.PlanningClock;
import com.athletiq.planning.PlanningErrors;
import com.athletiq.planning.SessionStatus;
import com.athletiq.user.UserName;
import com.athletiq.user.UserService;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suivi d'un athlète par son coach (US27 à US31) : en-tête, historique, prévu / réalisé et ressenti,
 * progression et records, en lecture seule (Q12) et calculés à la lecture (RM7).
 *
 * <p>Chaque lecture existe en deux temps. La méthode publique, appelée par le controller, valide les
 * paramètres (400), passe par l'{@code AccessGuard} (rôle COACH, athlète suivi par un lien actif ou séance
 * planifiée du coach : 404 sinon, jamais 403), puis délègue avec l'id du coach connecté. La méthode à
 * {@code coachId} explicite ne fait aucun contrôle d'accès : elle filtre chaque requête par ce coach (RM1),
 * et #11 la réutilisera pour les lectures de l'athlète. « Aujourd'hui » vient de {@link PlanningClock}.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    /** RM11 : une période d'historique couvre au plus 52 semaines, soit {@code to - from <= 364} jours. */
    static final int MAX_HISTORY_SPAN_DAYS = 364;
    /** Période par défaut : 4 semaines, {@code from = to - 27} jours. */
    static final int DEFAULT_HISTORY_SPAN_DAYS = 27;
    /** US30 : périodes de progression permises, en semaines (12 par défaut). */
    static final Set<Integer> PROGRESSION_WEEKS = Set.of(4, 12, 26, 52);
    /** Noms d'exercice triés comme en base (collation ai_ci) : sans tenir compte de la casse ni des accents. */
    private static final Comparator<String> NAME_ORDER = nameOrder();

    private final SessionPerformanceRepository performanceRepository;
    private final PerformanceMapper mapper;
    private final UserService userService;
    private final ExerciseService exerciseService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final PlanningClock planningClock;

    // ---------------------------------------------------------------- en-tête du suivi

    /** En-tête de la page « Suivi » : identité de l'athlète, aujourd'hui, et s'il a déjà un historique. */
    @Transactional(readOnly = true)
    public TrackingOverviewResponse overview(UUID athleteId) {
        accessGuard.assertCoachOfAthlete(athleteId);
        return overview(currentUser.id(), athleteId);
    }

    /** Sans contrôle d'accès : l'appelant a vérifié rôle et propriété. */
    @Transactional(readOnly = true)
    TrackingOverviewResponse overview(UUID coachId, UUID athleteId) {
        Objects.requireNonNull(coachId, "coachId");
        LocalDate today = planningClock.today();
        UserName athlete = userService.findNames(List.of(athleteId)).get(athleteId);
        boolean hasPastSessions = performanceRepository.countPastOrDone(coachId, athleteId, today) > 0;
        return new TrackingOverviewResponse(athleteId, athlete == null ? null : athlete.firstName(),
                athlete == null ? null : athlete.lastName(), today, hasPastSessions);
    }

    // ---------------------------------------------------------------- US27, US29 historique

    /**
     * US27 : séances réalisées et non faites (Q11) de ce coach pour cet athlète, de la plus récente à la plus
     * ancienne, paginées par le serveur. Les paramètres sont validés avant tout accès (400 {@code INVALID_PARAMETER}).
     */
    @Transactional(readOnly = true)
    public Page<SessionHistoryItem> sessionHistory(UUID athleteId, SessionHistoryFilter filter, Pageable pageable) {
        HistoryCriteria criteria = HistoryCriteria.of(filter, planningClock.today());
        accessGuard.assertCoachOfAthlete(athleteId);
        return sessionHistory(currentUser.id(), athleteId, criteria, pageable);
    }

    /** Sans contrôle d'accès : l'appelant a vérifié rôle et propriété, et validé les critères. */
    @Transactional(readOnly = true)
    Page<SessionHistoryItem> sessionHistory(UUID coachId, UUID athleteId, HistoryCriteria criteria,
                                            Pageable pageable) {
        Objects.requireNonNull(coachId, "coachId");
        LocalDate today = criteria.today();
        return performanceRepository.findHistory(coachId, athleteId, criteria.from(), criteria.to(), today,
                        criteria.includeDone(), criteria.includeMissed(), criteria.withComment(),
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(row -> mapper.toHistoryItem(row, SessionStatus.of(row.date(), today, row.done())));
    }

    /**
     * Critères validés de l'historique. Par défaut {@code to} = aujourd'hui et {@code from = to - 27} jours ;
     * refusés : {@code from > to}, plus de 52 semaines, statut {@code UPCOMING} (l'historique ne contient que
     * des séances réalisées ou non faites).
     */
    record HistoryCriteria(LocalDate from, LocalDate to, LocalDate today, boolean includeDone,
                           boolean includeMissed, boolean withComment) {

        static HistoryCriteria of(SessionHistoryFilter filter, LocalDate today) {
            LocalDate to = filter.to() != null ? filter.to() : today;
            LocalDate from = filter.from() != null ? filter.from() : to.minusDays(DEFAULT_HISTORY_SPAN_DAYS);
            if (from.isAfter(to)) {
                throw new InvalidParameterException("from");
            }
            if (ChronoUnit.DAYS.between(from, to) > MAX_HISTORY_SPAN_DAYS) {
                throw new InvalidParameterException("to");
            }
            SessionStatus status = filter.status();
            if (status == SessionStatus.UPCOMING) {
                throw new InvalidParameterException("status");
            }
            return new HistoryCriteria(from, to, today, status != SessionStatus.MISSED, status != SessionStatus.DONE,
                    filter.withComment());
        }
    }

    // ---------------------------------------------------------------- US28, US29 prévu / réalisé

    /** US28, US29 : prévu (instantané) en face du réalisé validé, série par série, et ressenti. */
    @Transactional(readOnly = true)
    public SessionReviewResponse review(UUID plannedSessionId) {
        accessGuard.assertPlannedSessionOwner(plannedSessionId);
        return review(currentUser.id(), plannedSessionId);
    }

    /** Sans contrôle d'accès : l'appelant a vérifié rôle et propriété. */
    @Transactional(readOnly = true)
    SessionReviewResponse review(UUID coachId, UUID plannedSessionId) {
        Objects.requireNonNull(coachId, "coachId");
        ReviewHeaderRow header = performanceRepository.findReviewHeader(coachId, plannedSessionId)
                .orElseThrow(PlanningErrors::plannedSessionNotFound);
        LocalDate today = planningClock.today();
        boolean done = header.done();
        List<ReviewExercise> exercises = reviewExercises(
                performanceRepository.findReviewLines(coachId, plannedSessionId), done);
        UserName athlete = userService.findNames(List.of(header.athleteId())).get(header.athleteId());
        return new SessionReviewResponse(header.plannedSessionId(), header.athleteId(),
                athlete == null ? null : athlete.firstName(), athlete == null ? null : athlete.lastName(),
                header.date(), today, header.name(), header.description(),
                SessionStatus.of(header.date(), today, done), done ? mapper.toFeedback(header) : null, exercises);
    }

    /** Une ligne par série prévue, regroupées par exercice de l'instantané et triées par position. */
    static List<ReviewExercise> reviewExercises(List<ReviewLineRow> lines, boolean sessionDone) {
        Map<UUID, List<ReviewLineRow>> byExercise = lines.stream()
                .collect(Collectors.groupingBy(ReviewLineRow::snapshotExerciseId, LinkedHashMap::new,
                        Collectors.toList()));
        return byExercise.values().stream()
                .map(exerciseLines -> reviewExercise(exerciseLines, sessionDone))
                .sorted(Comparator.comparingInt(ReviewExercise::position))
                .toList();
    }

    /**
     * Volumes (RM8) : Σ reps × charge, charge vide exclue. Poids du corps (toutes charges prévues vides) :
     * volumes {@code null}. Séance non réalisée : volume réalisé {@code null}. Une série non faite compte 0.
     */
    private static ReviewExercise reviewExercise(List<ReviewLineRow> lines, boolean sessionDone) {
        ReviewLineRow exercise = lines.getFirst();
        List<ReviewSet> sets = lines.stream()
                .filter(line -> line.snapshotSetId() != null)
                .sorted(Comparator.comparing(ReviewLineRow::setPosition))
                .map(line -> reviewSet(line, sessionDone))
                .toList();
        boolean bodyweight = sets.stream().allMatch(set -> set.plannedLoadKg() == null);
        BigDecimal plannedVolume = bodyweight ? null
                : sum(sets, set -> PerformanceMath.setVolume(set.plannedReps(), set.plannedLoadKg()));
        BigDecimal performedVolume = bodyweight || !sessionDone ? null
                : sum(sets, set -> PerformanceMath.setVolume(set.performedReps(), set.performedLoadKg()));
        return new ReviewExercise(exercise.snapshotExerciseId(), exercise.exerciseId(), exercise.exerciseName(),
                exercise.muscleGroup(), exercise.exercisePosition(), exercise.note(), bodyweight, plannedVolume,
                performedVolume, PerformanceMath.deltaPercent(plannedVolume, performedVolume), sets);
    }

    /** Hors séance réalisée, ou série sans ligne ou non faite : aucun réalisé ni écart. */
    private static ReviewSet reviewSet(ReviewLineRow line, boolean sessionDone) {
        BigDecimal plannedLoad = PerformanceMath.kg(line.plannedLoadKg());
        boolean setDone = sessionDone && line.performedReps() != null;
        Integer performedReps = setDone ? line.performedReps() : null;
        BigDecimal performedLoad = setDone ? PerformanceMath.kg(line.performedLoadKg()) : null;
        Integer repsDelta = setDone ? performedReps - line.plannedReps() : null;
        BigDecimal loadDelta = plannedLoad != null && performedLoad != null
                ? performedLoad.subtract(plannedLoad)
                : null;
        return new ReviewSet(line.snapshotSetId(), line.setPosition(), line.plannedReps(), plannedLoad,
                line.restSeconds(), setDone, performedReps, performedLoad, repsDelta, loadDelta);
    }

    /** Somme des volumes non vides, 0,0 si aucun. */
    private static BigDecimal sum(List<ReviewSet> sets, Function<ReviewSet, BigDecimal> volume) {
        return sets.stream()
                .map(volume)
                .filter(Objects::nonNull)
                .reduce(PerformanceMath.ZERO_KG, BigDecimal::add);
    }

    // ---------------------------------------------------------------- US30 exercices réalisés et progression

    /** US30 : exercices d'au moins une séance réalisée de cet athlète avec ce coach, pour choisir la progression. */
    @Transactional(readOnly = true)
    public List<PerformedExerciseResponse> performedExercises(UUID athleteId) {
        accessGuard.assertCoachOfAthlete(athleteId);
        return performedExercises(currentUser.id(), athleteId);
    }

    /** Sans contrôle d'accès : l'appelant a vérifié rôle et propriété. */
    @Transactional(readOnly = true)
    List<PerformedExerciseResponse> performedExercises(UUID coachId, UUID athleteId) {
        Objects.requireNonNull(coachId, "coachId");
        return byExercise(performanceRepository.findPerformedSetGroups(coachId, athleteId)).values().stream()
                .map(rows -> {
                    PerformedSetGroupRow latest = latest(rows);
                    int sessionCount = (int) rows.stream().map(PerformedSetGroupRow::plannedSessionId).distinct().count();
                    return new PerformedExerciseResponse(latest.exerciseId(), latest.exerciseName(),
                            latest.muscleGroup(), sessionCount, latest.date());
                })
                .sorted(Comparator.comparing((PerformedExerciseResponse exercise) -> exercise.muscleGroup().name())
                        .thenComparing(PerformedExerciseResponse::exerciseName, NAME_ORDER)
                        .thenComparing(PerformedExerciseResponse::exerciseId))
                .toList();
    }

    /**
     * US30 : un point par séance réalisée sur {@code weeks} semaines (4, 12, 26 ou 52, sinon 400), sans borne
     * haute (Q17). L'athlète doit être suivi (404 {@code ATHLETE_NOT_FOUND}) et l'exercice appartenir au coach
     * (404 {@code EXERCISE_NOT_FOUND}).
     */
    @Transactional(readOnly = true)
    public ProgressionResponse progression(UUID athleteId, UUID exerciseId, int weeks) {
        if (!PROGRESSION_WEEKS.contains(weeks)) {
            throw new InvalidParameterException("weeks");
        }
        accessGuard.assertCoachOfAthlete(athleteId);
        accessGuard.assertExerciseOwner(exerciseId);
        return progression(currentUser.id(), athleteId, exerciseId, weeks);
    }

    /** Sans contrôle d'accès : l'appelant a vérifié rôle et propriété, et la période. */
    @Transactional(readOnly = true)
    ProgressionResponse progression(UUID coachId, UUID athleteId, UUID exerciseId, int weeks) {
        Objects.requireNonNull(coachId, "coachId");
        LocalDate today = planningClock.today();
        LocalDate from = today.minusWeeks(weeks);
        List<ProgressionPoint> points = performanceRepository
                .findProgressionRows(coachId, athleteId, exerciseId, from).stream()
                .map(row -> new ProgressionPoint(row.plannedSessionId(), row.date(),
                        PerformanceMath.kg(row.maxLoadKg()), row.maxReps(), PerformanceMath.kg(row.volumeKg())))
                .toList();
        boolean bodyweight = points.stream().allMatch(point -> point.maxLoadKg() == null);
        ExerciseLabel label = performanceRepository
                .findLatestLabels(coachId, athleteId, exerciseId, PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElseGet(() -> libraryLabel(exerciseId));
        return new ProgressionResponse(athleteId, exerciseId, label.exerciseName(), label.muscleGroup(), bodyweight,
                weeks, from, today, points);
    }

    /** Exercice jamais réalisé avec ce coach : libellé de sa bibliothèque (lecture du coach connecté). */
    private ExerciseLabel libraryLabel(UUID exerciseId) {
        ExerciseResponse exercise = exerciseService.get(exerciseId);
        return new ExerciseLabel(exercise.name(), exercise.muscleGroup());
    }

    // ---------------------------------------------------------------- US31 records

    /** US31 : un record par exercice réalisé, toutes périodes, recalculé à chaque lecture (correction comprise). */
    @Transactional(readOnly = true)
    public PersonalRecordsResponse personalRecords(UUID athleteId) {
        accessGuard.assertCoachOfAthlete(athleteId);
        return personalRecords(currentUser.id(), athleteId);
    }

    /** Sans contrôle d'accès : l'appelant a vérifié rôle et propriété. */
    @Transactional(readOnly = true)
    PersonalRecordsResponse personalRecords(UUID coachId, UUID athleteId) {
        Objects.requireNonNull(coachId, "coachId");
        List<PersonalRecord> records = byExercise(performanceRepository.findPerformedSetGroups(coachId, athleteId))
                .values().stream()
                .map(PerformanceService::personalRecord)
                .sorted(Comparator.comparing((PersonalRecord record) -> record.muscleGroup().name())
                        .thenComparing(PersonalRecord::exerciseName, NAME_ORDER)
                        .thenComparing(PersonalRecord::exerciseId))
                .toList();
        return new PersonalRecordsResponse(athleteId, records);
    }

    /**
     * Q10 : la meilleure charge parmi les séries faites chargées, puis le plus de répétitions, puis la date la
     * plus ancienne ; sans série chargée (poids du corps), le plus de répétitions, puis la date la plus ancienne.
     */
    private static PersonalRecord personalRecord(List<PerformedSetGroupRow> rows) {
        List<PerformedSetGroupRow> loaded = rows.stream().filter(row -> row.loadKg() != null).toList();
        boolean bodyweight = loaded.isEmpty();
        Comparator<PerformedSetGroupRow> mostReps =
                Comparator.comparing(PerformedSetGroupRow::maxReps, Comparator.reverseOrder());
        Comparator<PerformedSetGroupRow> earliest = Comparator.comparing(PerformedSetGroupRow::date)
                .thenComparing(PerformedSetGroupRow::createdAt)
                .thenComparing(PerformedSetGroupRow::plannedSessionId);
        PerformedSetGroupRow best = bodyweight
                ? rows.stream().min(mostReps.thenComparing(earliest)).orElseThrow()
                : loaded.stream().min(Comparator.comparing(PerformedSetGroupRow::loadKg, Comparator.reverseOrder())
                        .thenComparing(mostReps)
                        .thenComparing(earliest)).orElseThrow();
        PerformedSetGroupRow label = latest(rows);
        return new PersonalRecord(label.exerciseId(), label.exerciseName(), label.muscleGroup(), bodyweight,
                PerformanceMath.kg(best.loadKg()), best.maxReps(), best.date(), best.plannedSessionId());
    }

    /** RM6 : regroupement par exercice de la bibliothèque ({@code exercise_id} de l'instantané). */
    private static Map<UUID, List<PerformedSetGroupRow>> byExercise(List<PerformedSetGroupRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(PerformedSetGroupRow::exerciseId, LinkedHashMap::new,
                Collectors.toList()));
    }

    /** RM6 : la ligne de la séance réalisée la plus récente porte le libellé à afficher. */
    private static PerformedSetGroupRow latest(List<PerformedSetGroupRow> rows) {
        return rows.stream()
                .max(Comparator.comparing(PerformedSetGroupRow::date)
                        .thenComparing(PerformedSetGroupRow::createdAt)
                        .thenComparing(PerformedSetGroupRow::plannedSessionId))
                .orElseThrow();
    }

    private static Comparator<String> nameOrder() {
        Collator collator = Collator.getInstance(Locale.FRENCH);
        collator.setStrength(Collator.PRIMARY);
        return collator::compare;
    }
}
