package com.athletiq.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.ExerciseErrors;
import com.athletiq.exercise.ExerciseService;
import com.athletiq.exercise.MuscleGroup;
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
import com.athletiq.planning.PlanningProperties;
import com.athletiq.planning.SessionStatus;
import com.athletiq.user.UserName;
import com.athletiq.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Suivi d'un athlète, sans base. L'horloge est fixée au 4 novembre 2026 à 23 h 30 UTC, soit le 5 novembre à
 * 0 h 30 à Paris : « aujourd'hui » est le 5, et le 4 est déjà passé.
 */
@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T23:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-03T18:00:00Z");
    private static final Instant CORRECTED_AT = Instant.parse("2026-11-04T07:30:00Z");

    @Mock
    private SessionPerformanceRepository performanceRepository;
    @Mock
    private UserService userService;
    @Mock
    private ExerciseService exerciseService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private PerformanceService service;
    private final UUID coachId = UUID.randomUUID();
    private final UUID athleteId = UUID.randomUUID();
    private final UUID plannedSessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PlanningClock clock = new PlanningClock(Clock.fixed(NOW, ZoneOffset.UTC),
                new PlanningProperties(PlanningProperties.DEFAULT_ZONE));
        service = new PerformanceService(performanceRepository, new PerformanceMapperImpl(), userService,
                exerciseService, accessGuard, currentUser, clock);
        lenient().when(currentUser.id()).thenReturn(coachId);
        lenient().when(userService.findNames(List.of(athleteId)))
                .thenReturn(Map.of(athleteId, new UserName("Lucas", "Girard")));
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    private static BigDecimal kg(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    // ---------------------------------------------------------------- en-tête

    @Test
    void theOverviewNamesTheAthleteAndSaysWhetherHeHasAHistory() {
        when(performanceRepository.countPastOrDone(coachId, athleteId, TODAY)).thenReturn(3L);

        TrackingOverviewResponse overview = service.overview(athleteId);

        verify(accessGuard).assertCoachOfAthlete(athleteId);
        assertThat(overview).isEqualTo(new TrackingOverviewResponse(athleteId, "Lucas", "Girard", TODAY, true));
    }

    @Test
    void anAthleteWithoutPastNorDoneSessionHasNoHistory() {
        when(performanceRepository.countPastOrDone(coachId, athleteId, TODAY)).thenReturn(0L);

        assertThat(service.overview(athleteId).hasPastSessions()).isFalse();
    }

    @Test
    void theOverviewOfAnAthleteNotFollowedAnswers404() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.overview(athleteId));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(performanceRepository, userService);
    }

    // ---------------------------------------------------------------- US27, US29 historique

    private static SessionHistoryRow doneRow(LocalDate date) {
        return new SessionHistoryRow(UUID.randomUUID(), date, "Haut du corps A", UUID.randomUUID(), 8, 4, true,
                VALIDATED_AT, CORRECTED_AT);
    }

    private static SessionHistoryRow missedRow(LocalDate date) {
        return new SessionHistoryRow(UUID.randomUUID(), date, "Bas du corps A", null, null, null, false, null, null);
    }

    private static SessionHistoryFilter filter(LocalDate from, LocalDate to, SessionStatus status) {
        return new SessionHistoryFilter(from, to, status, false);
    }

    @Test
    void theHistoryCoversTheFourWeeksUntilTodayByDefaultWithDoneAndMissedSessions() {
        SessionHistoryRow done = doneRow(YESTERDAY);
        SessionHistoryRow missed = missedRow(TODAY.minusDays(3));
        when(performanceRepository.findHistory(coachId, athleteId, TODAY.minusDays(27), TODAY, TODAY, true, true,
                false, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(done, missed), PageRequest.of(0, 20), 2));

        Page<SessionHistoryItem> page = service.sessionHistory(athleteId, filter(null, null, null),
                PageRequest.of(0, 20));

        verify(accessGuard).assertCoachOfAthlete(athleteId);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).containsExactly(
                new SessionHistoryItem(done.plannedSessionId(), YESTERDAY, "Haut du corps A", SessionStatus.DONE, 8, 4,
                        true, VALIDATED_AT, CORRECTED_AT),
                new SessionHistoryItem(missed.plannedSessionId(), TODAY.minusDays(3), "Bas du corps A",
                        SessionStatus.MISSED, null, null, false, null, null));
    }

    @Test
    void aSessionValidatedBeforeItsDateIsDoneInTheHistory() {
        SessionHistoryRow ahead = doneRow(TODAY.plusDays(2));
        when(performanceRepository.findHistory(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(ahead)));

        Page<SessionHistoryItem> page = service.sessionHistory(athleteId,
                filter(TODAY, TODAY.plusDays(6), null), PageRequest.of(0, 20));

        assertThat(page.getContent().getFirst().status()).isEqualTo(SessionStatus.DONE);
    }

    @Test
    void theStatusAndCommentFiltersAndThePeriodAreHandedToTheQuery() {
        when(performanceRepository.findHistory(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any())).thenReturn(Page.empty());
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);

        service.sessionHistory(athleteId, new SessionHistoryFilter(from, to, SessionStatus.DONE, true),
                PageRequest.of(2, 50));
        service.sessionHistory(athleteId, filter(from, null, SessionStatus.MISSED), PageRequest.of(0, 20));
        service.sessionHistory(athleteId, filter(null, to, null), PageRequest.of(0, 20));

        verify(performanceRepository).findHistory(coachId, athleteId, from, to, TODAY, true, false, true,
                PageRequest.of(2, 50));
        verify(performanceRepository).findHistory(coachId, athleteId, from, TODAY, TODAY, false, true, false,
                PageRequest.of(0, 20));
        verify(performanceRepository).findHistory(coachId, athleteId, to.minusDays(27), to, TODAY, true, true, false,
                PageRequest.of(0, 20));
    }

    @Test
    void fiftyTwoWeeksIsTheLongestPeriod() {
        when(performanceRepository.findHistory(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any())).thenReturn(Page.empty());

        service.sessionHistory(athleteId, filter(TODAY.minusDays(364), TODAY, null), PageRequest.of(0, 20));

        verify(performanceRepository).findHistory(eq(coachId), eq(athleteId), eq(TODAY.minusDays(364)), eq(TODAY),
                eq(TODAY), eq(true), eq(true), eq(false), any());
    }

    @Test
    void invalidHistoryParametersAnswer400BeforeAnyLookup() {
        List<SessionHistoryFilter> refused = List.of(
                filter(TODAY, YESTERDAY, null),
                filter(TODAY.minusDays(365), TODAY, null),
                filter(TODAY.plusDays(1), null, null),
                filter(null, null, SessionStatus.UPCOMING));

        for (SessionHistoryFilter invalid : refused) {
            Throwable thrown = catchThrowable(() -> service.sessionHistory(athleteId, invalid, PageRequest.of(0, 20)));
            assertDomainError(thrown, InvalidParameterException.class, ErrorCodes.INVALID_PARAMETER);
        }
        verifyNoInteractions(accessGuard, performanceRepository);
    }

    @Test
    void theHistoryOfAnAthleteNotFollowedAnswers404() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.sessionHistory(athleteId, filter(null, null, null),
                PageRequest.of(0, 20)));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(performanceRepository);
    }

    // ---------------------------------------------------------------- US28, US29 prévu / réalisé

    private final UUID benchRow = UUID.randomUUID();
    private final UUID pullUpRow = UUID.randomUUID();
    private final UUID benchId = UUID.randomUUID();
    private final UUID pullUpId = UUID.randomUUID();

    private ReviewLineRow bench(int position, int plannedReps, String plannedLoad, Integer performedReps,
                                String performedLoad) {
        return new ReviewLineRow(benchRow, benchId, "Développé couché", MuscleGroup.PECTORAUX, 0, "Tempo 3-1-1",
                UUID.randomUUID(), position, plannedReps, kg(plannedLoad), 90, performedReps, kg(performedLoad));
    }

    private ReviewLineRow pullUp(int position, int plannedReps, Integer performedReps, String performedLoad) {
        return new ReviewLineRow(pullUpRow, pullUpId, "Tractions", MuscleGroup.DOS, 1, null, UUID.randomUUID(),
                position, plannedReps, null, null, performedReps, kg(performedLoad));
    }

    private ReviewHeaderRow header(LocalDate date, boolean done) {
        return done
                ? new ReviewHeaderRow(plannedSessionId, athleteId, date, "Haut du corps A", "Consignes",
                UUID.randomUUID(), 8, 4, "Dernière série difficile", VALIDATED_AT, CORRECTED_AT)
                : new ReviewHeaderRow(plannedSessionId, athleteId, date, "Haut du corps A", "Consignes", null, null,
                null, null, null, null);
    }

    /** Développé couché 8 × 60, 8 × 60, 6 × 65 (une série sans ligne), puis tractions au poids du corps. */
    private List<ReviewLineRow> lines() {
        return List.of(
                pullUp(1, 8, null, null),
                pullUp(0, 8, 10, "10.0"),
                bench(2, 6, "65.0", null, null),
                bench(1, 8, "60.0", 6, "60.0"),
                bench(0, 8, "60.0", 8, "62.5"));
    }

    @Test
    void aDoneSessionComparesEachSetAndTheVolumeOfEachExercise() {
        when(performanceRepository.findReviewHeader(coachId, plannedSessionId))
                .thenReturn(Optional.of(header(YESTERDAY, true)));
        when(performanceRepository.findReviewLines(coachId, plannedSessionId)).thenReturn(lines());

        SessionReviewResponse review = service.review(plannedSessionId);

        verify(accessGuard).assertPlannedSessionOwner(plannedSessionId);
        assertThat(review.status()).isEqualTo(SessionStatus.DONE);
        assertThat(review.today()).isEqualTo(TODAY);
        assertThat(review.athleteFirstName()).isEqualTo("Lucas");
        assertThat(review.description()).isEqualTo("Consignes");
        assertThat(review.feedback().rpe()).isEqualTo(8);
        assertThat(review.feedback().fatigue()).isEqualTo(4);
        assertThat(review.feedback().comment()).isEqualTo("Dernière série difficile");
        assertThat(review.feedback().validatedAt()).isEqualTo(VALIDATED_AT);
        assertThat(review.feedback().correctedAt()).isEqualTo(CORRECTED_AT);
        assertThat(review.exercises()).extracting(ReviewExercise::exerciseName)
                .containsExactly("Développé couché", "Tractions");

        ReviewExercise bench = review.exercises().getFirst();
        assertThat(bench.bodyweight()).isFalse();
        assertThat(bench.note()).isEqualTo("Tempo 3-1-1");
        assertThat(bench.sets()).extracting(ReviewSet::position).containsExactly(0, 1, 2);
        // +2,5 kg à répétitions égales ; -2 répétitions à charge égale ; série sans ligne : non faite.
        assertThat(bench.sets().get(0)).isEqualTo(new ReviewSet(bench.sets().get(0).snapshotSetId(), 0, 8, kg("60.0"),
                90, true, 8, kg("62.5"), 0, kg("2.5")));
        assertThat(bench.sets().get(1).repsDelta()).isEqualTo(-2);
        assertThat(bench.sets().get(1).loadDeltaKg()).isEqualTo(kg("0.0"));
        assertThat(bench.sets().get(2).done()).isFalse();
        assertThat(bench.sets().get(2).performedReps()).isNull();
        assertThat(bench.sets().get(2).performedLoadKg()).isNull();
        assertThat(bench.sets().get(2).repsDelta()).isNull();
        // Prévu 8 × 60 + 8 × 60 + 6 × 65 = 1350 ; réalisé 8 × 62,5 + 6 × 60 + 0 = 860 ; écart -36 %.
        assertThat(bench.plannedVolumeKg()).isEqualTo(kg("1350.0"));
        assertThat(bench.performedVolumeKg()).isEqualTo(kg("860.0"));
        assertThat(bench.volumeDeltaPercent()).isEqualTo(-36);

        // Poids du corps : pas de volume, pas d'écart de charge, comparaison sur les répétitions.
        ReviewExercise pullUp = review.exercises().get(1);
        assertThat(pullUp.bodyweight()).isTrue();
        assertThat(pullUp.plannedVolumeKg()).isNull();
        assertThat(pullUp.performedVolumeKg()).isNull();
        assertThat(pullUp.volumeDeltaPercent()).isNull();
        assertThat(pullUp.sets().getFirst().repsDelta()).isEqualTo(2);
        assertThat(pullUp.sets().getFirst().performedLoadKg()).isEqualTo(kg("10.0"));
        assertThat(pullUp.sets().getFirst().loadDeltaKg()).isNull();
        assertThat(pullUp.sets().get(1).done()).isFalse();
    }

    @Test
    void aDoneSessionWithEverySetNotDoneHasAZeroPerformedVolume() {
        when(performanceRepository.findReviewHeader(coachId, plannedSessionId))
                .thenReturn(Optional.of(header(YESTERDAY, true)));
        when(performanceRepository.findReviewLines(coachId, plannedSessionId)).thenReturn(List.of(
                bench(0, 8, "60.0", null, null), bench(1, 8, "60.0", null, null)));

        ReviewExercise bench = service.review(plannedSessionId).exercises().getFirst();

        assertThat(bench.plannedVolumeKg()).isEqualTo(kg("960.0"));
        assertThat(bench.performedVolumeKg()).isEqualTo(kg("0.0"));
        assertThat(bench.volumeDeltaPercent()).isEqualTo(-100);
    }

    @Test
    void aMissedOrUpcomingSessionShowsThePlannedOnly() {
        when(performanceRepository.findReviewLines(coachId, plannedSessionId)).thenReturn(lines());
        when(performanceRepository.findReviewHeader(coachId, plannedSessionId))
                .thenReturn(Optional.of(header(YESTERDAY, false)))
                .thenReturn(Optional.of(header(TODAY, false)));

        SessionReviewResponse missed = service.review(plannedSessionId);
        SessionReviewResponse upcoming = service.review(plannedSessionId);

        assertThat(missed.status()).isEqualTo(SessionStatus.MISSED);
        assertThat(upcoming.status()).isEqualTo(SessionStatus.UPCOMING);
        for (SessionReviewResponse review : List.of(missed, upcoming)) {
            assertThat(review.feedback()).isNull();
            ReviewExercise bench = review.exercises().getFirst();
            assertThat(bench.plannedVolumeKg()).isEqualTo(kg("1350.0"));
            assertThat(bench.performedVolumeKg()).isNull();
            assertThat(bench.volumeDeltaPercent()).isNull();
            // Les lignes réalisées d'un brouillon ne sont jamais lues : rien n'est fait.
            assertThat(review.exercises()).flatExtracting(ReviewExercise::sets).allSatisfy(set -> {
                assertThat(set.done()).isFalse();
                assertThat(set.performedReps()).isNull();
                assertThat(set.performedLoadKg()).isNull();
                assertThat(set.repsDelta()).isNull();
                assertThat(set.loadDeltaKg()).isNull();
            });
        }
    }

    @Test
    void aSessionValidatedBeforeItsDateIsDone() {
        when(performanceRepository.findReviewHeader(coachId, plannedSessionId))
                .thenReturn(Optional.of(header(TODAY.plusDays(3), true)));
        when(performanceRepository.findReviewLines(coachId, plannedSessionId)).thenReturn(lines());

        SessionReviewResponse review = service.review(plannedSessionId);

        assertThat(review.status()).isEqualTo(SessionStatus.DONE);
        assertThat(review.feedback()).isNotNull();
    }

    @Test
    void theReviewOfAPlannedSessionOfAnotherCoachOrOfAClosedLinkAnswers404() {
        doThrow(PlanningErrors.plannedSessionNotFound()).when(accessGuard).assertPlannedSessionOwner(plannedSessionId);

        Throwable thrown = catchThrowable(() -> service.review(plannedSessionId));

        assertDomainError(thrown, ResourceNotFoundException.class, PlanningErrors.PLANNED_SESSION_NOT_FOUND);
        verifyNoInteractions(performanceRepository, userService);
    }

    @Test
    void aPlannedSessionGoneAfterTheGuardAnswers404() {
        when(performanceRepository.findReviewHeader(coachId, plannedSessionId)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.review(plannedSessionId));

        assertDomainError(thrown, ResourceNotFoundException.class, PlanningErrors.PLANNED_SESSION_NOT_FOUND);
    }

    // ---------------------------------------------------------------- US30 exercices réalisés et progression

    private static final Instant EARLY = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant LATE = Instant.parse("2026-09-01T09:00:00Z");
    private final UUID squatId = UUID.randomUUID();
    private final UUID raiseId = UUID.randomUUID();
    private final UUID facePullId = UUID.randomUUID();

    private static PerformedSetGroupRow group(UUID exerciseId, String name, MuscleGroup muscleGroup, UUID sessionId,
                                              LocalDate date, Instant createdAt, String loadKg, int maxReps) {
        return new PerformedSetGroupRow(exerciseId, name, muscleGroup, sessionId, date, createdAt, kg(loadKg), maxReps);
    }

    @Test
    void thePerformedExercisesCountTheirSessionsAndTakeTheLabelOfTheLatestSnapshot() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        UUID sameDayLater = UUID.randomUUID();
        when(performanceRepository.findPerformedSetGroups(coachId, athleteId)).thenReturn(List.of(
                group(squatId, "Squat", MuscleGroup.QUADRICEPS, older, TODAY.minusDays(20), EARLY, "100.0", 5),
                group(squatId, "Squat", MuscleGroup.QUADRICEPS, older, TODAY.minusDays(20), EARLY, "110.0", 3),
                group(squatId, "Squat barre", MuscleGroup.QUADRICEPS, newer, TODAY.minusDays(3), EARLY, "105.0", 5),
                group(squatId, "Squat arrière", MuscleGroup.QUADRICEPS, sameDayLater, TODAY.minusDays(3), LATE,
                        "100.0", 6),
                group(facePullId, "Face pull", MuscleGroup.EPAULES, older, TODAY.minusDays(20), EARLY, "20.0", 12),
                group(raiseId, "Élévations latérales", MuscleGroup.EPAULES, newer, TODAY.minusDays(3), EARLY, "8.0", 15),
                group(pullUpId, "Tractions", MuscleGroup.DOS, newer, TODAY.minusDays(3), EARLY, null, 8)));

        List<PerformedExerciseResponse> exercises = service.performedExercises(athleteId);

        verify(accessGuard).assertCoachOfAthlete(athleteId);
        // Groupe musculaire (code), puis nom sans tenir compte des accents : « Élévations » avant « Face pull ».
        assertThat(exercises).extracting(PerformedExerciseResponse::exerciseName)
                .containsExactly("Tractions", "Élévations latérales", "Face pull", "Squat arrière");
        PerformedExerciseResponse squat = exercises.get(3);
        assertThat(squat.exerciseId()).isEqualTo(squatId);
        assertThat(squat.sessionCount()).isEqualTo(3);
        assertThat(squat.lastPerformedOn()).isEqualTo(TODAY.minusDays(3));
        assertThat(exercises.getFirst().muscleGroup()).isEqualTo(MuscleGroup.DOS);
    }

    @Test
    void thePerformedExercisesOfAnAthleteNotFollowedAnswer404() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.performedExercises(athleteId));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(performanceRepository);
    }

    @Test
    void theProgressionHasOnePointPerDoneSessionSinceWeeksAgoWithTheLatestLabel() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(performanceRepository.findProgressionRows(coachId, athleteId, squatId, TODAY.minusDays(84)))
                .thenReturn(List.of(
                        new ProgressionRow(first, TODAY.minusDays(30), EARLY, kg("110"), 5, kg("1330.00")),
                        new ProgressionRow(second, TODAY.plusDays(1), EARLY, kg("115.0"), 5, kg("575.0"))));
        when(performanceRepository.findLatestLabels(coachId, athleteId, squatId, PageRequest.of(0, 1)))
                .thenReturn(List.of(new ExerciseLabel("Squat barre", MuscleGroup.QUADRICEPS)));

        ProgressionResponse progression = service.progression(athleteId, squatId, 12);

        InOrder order = inOrder(accessGuard);
        order.verify(accessGuard).assertCoachOfAthlete(athleteId);
        order.verify(accessGuard).assertExerciseOwner(squatId);
        assertThat(progression.weeks()).isEqualTo(12);
        assertThat(progression.from()).isEqualTo(TODAY.minusDays(84));
        assertThat(progression.today()).isEqualTo(TODAY);
        assertThat(progression.exerciseName()).isEqualTo("Squat barre");
        assertThat(progression.muscleGroup()).isEqualTo(MuscleGroup.QUADRICEPS);
        assertThat(progression.bodyweight()).isFalse();
        assertThat(progression.points()).containsExactly(
                new ProgressionPoint(first, TODAY.minusDays(30), kg("110.0"), 5, kg("1330.0")),
                new ProgressionPoint(second, TODAY.plusDays(1), kg("115.0"), 5, kg("575.0")));
        verifyNoInteractions(exerciseService);
    }

    @Test
    void aBodyweightProgressionFollowsTheMaxReps() {
        when(performanceRepository.findProgressionRows(any(), any(), any(), any())).thenReturn(List.of(
                new ProgressionRow(UUID.randomUUID(), TODAY.minusDays(3), EARLY, null, 10, null)));
        when(performanceRepository.findLatestLabels(any(), any(), any(), any()))
                .thenReturn(List.of(new ExerciseLabel("Tractions", MuscleGroup.DOS)));

        ProgressionResponse progression = service.progression(athleteId, pullUpId, 4);

        assertThat(progression.bodyweight()).isTrue();
        assertThat(progression.from()).isEqualTo(TODAY.minusDays(28));
        assertThat(progression.points()).singleElement().satisfies(point -> {
            assertThat(point.maxReps()).isEqualTo(10);
            assertThat(point.maxLoadKg()).isNull();
            assertThat(point.volumeKg()).isNull();
        });
    }

    @Test
    void anExerciseNeverPerformedHasNoPointAndTheLabelOfTheLibrary() {
        when(performanceRepository.findProgressionRows(any(), any(), any(), any())).thenReturn(List.of());
        when(performanceRepository.findLatestLabels(any(), any(), any(), any())).thenReturn(List.of());
        when(exerciseService.get(squatId)).thenReturn(new ExerciseResponse(squatId, "Squat", null,
                MuscleGroup.QUADRICEPS, null, null, NOW, NOW));

        ProgressionResponse progression = service.progression(athleteId, squatId, 52);

        assertThat(progression.points()).isEmpty();
        assertThat(progression.exerciseName()).isEqualTo("Squat");
        assertThat(progression.muscleGroup()).isEqualTo(MuscleGroup.QUADRICEPS);
        assertThat(progression.from()).isEqualTo(TODAY.minusDays(364));
    }

    @Test
    void aProgressionPeriodOtherThan4Or12Or26Or52WeeksAnswers400BeforeAnyLookup() {
        for (int weeks : List.of(0, 1, 8, 13, 53, -4)) {
            Throwable thrown = catchThrowable(() -> service.progression(athleteId, squatId, weeks));
            assertDomainError(thrown, InvalidParameterException.class, ErrorCodes.INVALID_PARAMETER);
        }
        verifyNoInteractions(accessGuard, performanceRepository);
    }

    @Test
    void theProgressionOfAnAthleteNotFollowedOrOfAnotherCoachsExerciseAnswers404() {
        UUID otherAthlete = UUID.randomUUID();
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);
        doThrow(ExerciseErrors.exerciseNotFound()).when(accessGuard).assertExerciseOwner(squatId);

        Throwable athlete = catchThrowable(() -> service.progression(athleteId, squatId, 12));
        Throwable exercise = catchThrowable(() -> service.progression(otherAthlete, squatId, 12));

        assertDomainError(athlete, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        assertDomainError(exercise, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
        // Athlète d'abord : l'exercice n'est contrôlé que pour un athlète suivi.
        verify(accessGuard).assertExerciseOwner(squatId);
        verifyNoInteractions(performanceRepository);
    }

    // ---------------------------------------------------------------- US31 records

    @Test
    void aRecordIsTheBestLoadThenMoreRepsThenTheEarliestDate() {
        UUID early = UUID.randomUUID();
        UUID middle = UUID.randomUUID();
        UUID late = UUID.randomUUID();
        when(performanceRepository.findPerformedSetGroups(coachId, athleteId)).thenReturn(List.of(
                group(squatId, "Squat", MuscleGroup.QUADRICEPS, early, TODAY.minusDays(30), EARLY, "110.0", 3),
                group(squatId, "Squat", MuscleGroup.QUADRICEPS, middle, TODAY.minusDays(20), EARLY, "110.0", 5),
                group(squatId, "Squat", MuscleGroup.QUADRICEPS, middle, TODAY.minusDays(20), EARLY, "100.0", 8),
                group(squatId, "Squat barre", MuscleGroup.QUADRICEPS, late, TODAY.minusDays(10), EARLY, "110.0", 5),
                group(squatId, "Squat barre", MuscleGroup.QUADRICEPS, late, TODAY.minusDays(10), EARLY, null, 20)));

        PersonalRecordsResponse records = service.personalRecords(athleteId);

        verify(accessGuard).assertCoachOfAthlete(athleteId);
        assertThat(records.athleteId()).isEqualTo(athleteId);
        // 110 kg : 5 répétitions battent 3 ; à 110 × 5, la date la plus ancienne l'emporte. Libellé le plus récent.
        assertThat(records.records()).containsExactly(new PersonalRecord(squatId, "Squat barre",
                MuscleGroup.QUADRICEPS, false, kg("110.0"), 5, TODAY.minusDays(20), middle));
    }

    @Test
    void aBodyweightRecordIsTheMostRepsThenTheEarliestDate() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(performanceRepository.findPerformedSetGroups(coachId, athleteId)).thenReturn(List.of(
                group(pullUpId, "Tractions", MuscleGroup.DOS, second, TODAY.minusDays(5), EARLY, null, 12),
                group(pullUpId, "Tractions", MuscleGroup.DOS, first, TODAY.minusDays(12), EARLY, null, 12)));

        PersonalRecord record = service.personalRecords(athleteId).records().getFirst();

        assertThat(record.bodyweight()).isTrue();
        assertThat(record.loadKg()).isNull();
        assertThat(record.reps()).isEqualTo(12);
        assertThat(record.date()).isEqualTo(TODAY.minusDays(12));
        assertThat(record.plannedSessionId()).isEqualTo(first);
    }

    @Test
    void recordsAreSortedByMuscleGroupThenNameAndEmptyWithoutDoneSession() {
        when(performanceRepository.findPerformedSetGroups(coachId, athleteId))
                .thenReturn(List.of(
                        group(squatId, "Squat", MuscleGroup.QUADRICEPS, plannedSessionId, YESTERDAY, EARLY, "100.0", 5),
                        group(facePullId, "Face pull", MuscleGroup.EPAULES, plannedSessionId, YESTERDAY, EARLY, "20.0",
                                12),
                        group(raiseId, "élévations", MuscleGroup.EPAULES, plannedSessionId, YESTERDAY, EARLY, "8.0",
                                15)))
                .thenReturn(List.of());

        assertThat(service.personalRecords(athleteId).records()).extracting(PersonalRecord::exerciseName)
                .containsExactly("élévations", "Face pull", "Squat");
        assertThat(service.personalRecords(athleteId).records()).isEmpty();
    }

    @Test
    void theRecordsOfAnAthleteNotFollowedAnswer404() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.personalRecords(athleteId));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(performanceRepository);
    }
}
