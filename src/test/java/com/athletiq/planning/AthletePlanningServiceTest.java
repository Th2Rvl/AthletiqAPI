package com.athletiq.planning;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.planning.dto.AthleteSessionDetailResponse;
import com.athletiq.planning.dto.AthleteSessionListResponse;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AthletePlanningServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Mock
    private PlannedSessionRepository plannedSessionRepository;
    @Mock
    private SessionSnapshotRepository snapshotRepository;
    @Mock
    private SessionSnapshotExerciseRepository snapshotExerciseRepository;
    @Mock
    private PlannedSessionCompletion completion;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private AthletePlanningService service;
    private User athlete;
    private User coach;

    @BeforeEach
    void setUp() {
        PlanningClock clock = new PlanningClock(Clock.fixed(NOW, ZoneOffset.UTC), new PlanningProperties(null));
        service = new AthletePlanningService(plannedSessionRepository, snapshotRepository,
                snapshotExerciseRepository, completion, clock, accessGuard, currentUser);
        athlete = user("lea@mail.fr", Role.ATHLETE);
        coach = user("coach@club.fr", Role.COACH);
        org.mockito.Mockito.lenient().when(currentUser.id()).thenReturn(athlete.getId());
        org.mockito.Mockito.lenient().when(completion.findValidated(any())).thenReturn(java.util.Set.of());
        org.mockito.Mockito.lenient().when(completion.summarize(any())).thenReturn(java.util.Map.of());
    }

    private AthleteSessionRow row(LocalDate date, UUID snapshotId) {
        return new AthleteSessionRow(UUID.randomUUID(), date, snapshotId, "Haut du corps A", coach.getId(),
                "Nicolas", "Petit", NOW);
    }

    @Test
    void calendarListsTheSessionsWithTheirCounts() {
        UUID snapshotId = UUID.randomUUID();
        when(plannedSessionRepository.findOwnRows(athlete.getId(), TODAY, TODAY))
                .thenReturn(List.of(row(TODAY, snapshotId)));
        when(snapshotExerciseRepository.countBySnapshot(any()))
                .thenReturn(List.of(new SnapshotCount(snapshotId, 2, 7)));

        AthleteSessionListResponse response = service.calendar(TODAY, TODAY);

        assertThat(response.today()).isEqualTo(TODAY);
        assertThat(response.sessions()).singleElement().satisfies(session -> {
            assertThat(session.exerciseCount()).isEqualTo(2);
            assertThat(session.setCount()).isEqualTo(7);
            assertThat(session.status()).isEqualTo(SessionStatus.UPCOMING);
            assertThat(session.coachLastName()).isEqualTo("Petit");
            assertThat(session.rpe()).isNull();
            assertThat(session.doneSetCount()).isNull();
        });
        verify(accessGuard).assertAthleteWithCoach();
    }

    @Test
    void aDoneSessionCarriesItsRpeDoneSetsAndComment() {
        UUID snapshotId = UUID.randomUUID();
        AthleteSessionRow done = row(TODAY.minusDays(1), snapshotId);
        when(plannedSessionRepository.findOwnRows(athlete.getId(), TODAY.minusDays(7), TODAY))
                .thenReturn(List.of(done));
        when(snapshotExerciseRepository.countBySnapshot(any())).thenReturn(List.of(new SnapshotCount(snapshotId, 4, 14)));
        when(completion.summarize(any())).thenReturn(java.util.Map.of(done.id(), new CompletionSummary(8, 13, true)));

        assertThat(service.calendar(TODAY.minusDays(7), TODAY).sessions()).singleElement().satisfies(session -> {
            assertThat(session.status()).isEqualTo(SessionStatus.DONE);
            assertThat(session.rpe()).isEqualTo(8);
            assertThat(session.doneSetCount()).isEqualTo(13);
            assertThat(session.setCount()).isEqualTo(14);
            assertThat(session.hasComment()).isTrue();
        });
    }

    @Test
    void theHomeHasTheTodaySessionsInDetailTheNextSessionAndTheLastDoneOne() {
        UUID snapshotId = UUID.randomUUID();
        SessionSnapshot snapshot = withId(SessionSnapshot.builder().coach(coach).name("Bas du corps").build(), snapshotId);
        PlannedSession planned = withId(PlannedSession.builder().coach(coach).athlete(athlete).scheduledDate(TODAY)
                .snapshot(snapshot).build(), UUID.randomUUID());
        AthleteSessionRow todayRow = new AthleteSessionRow(planned.getId(), TODAY, snapshotId, "Bas du corps",
                coach.getId(), "Nicolas", "Petit", NOW);
        AthleteSessionRow nextRow = row(TODAY.plusDays(2), UUID.randomUUID());
        AthleteSessionRow lastRow = row(TODAY.minusDays(1), UUID.randomUUID());
        when(plannedSessionRepository.findOwnRows(athlete.getId(), TODAY, TODAY)).thenReturn(List.of(todayRow));
        when(plannedSessionRepository.findOwnById(planned.getId(), athlete.getId())).thenReturn(Optional.of(planned));
        when(snapshotRepository.findDetailById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(plannedSessionRepository.findOwnRowsAfter(eq(athlete.getId()), eq(TODAY), any()))
                .thenReturn(List.of(nextRow));
        when(snapshotExerciseRepository.findNamesBySnapshot(nextRow.snapshotId()))
                .thenReturn(List.of("Développé couché", "Curl"));
        when(plannedSessionRepository.findOwnRows(athlete.getId(), TODAY.minusDays(28), TODAY))
                .thenReturn(List.of(lastRow, todayRow));
        when(completion.summarize(any())).thenReturn(java.util.Map.of(lastRow.id(), new CompletionSummary(8, 13, true)));
        when(snapshotExerciseRepository.countBySnapshot(any()))
                .thenReturn(List.of(new SnapshotCount(lastRow.snapshotId(), 4, 14)));

        var home = service.today();

        assertThat(home.today()).isEqualTo(TODAY);
        assertThat(home.sessions()).extracting(s -> s.name()).containsExactly("Bas du corps");
        assertThat(home.next().exerciseNames()).containsExactly("Développé couché", "Curl");
        assertThat(home.next().date()).isEqualTo(TODAY.plusDays(2));
        assertThat(home.last().id()).isEqualTo(lastRow.id());
        assertThat(home.last().rpe()).isEqualTo(8);
        assertThat(home.last().doneSetCount()).isEqualTo(13);
        assertThat(home.last().setCount()).isEqualTo(14);
    }

    @Test
    void theHomeIsEmptyWithoutAnySession() {
        when(plannedSessionRepository.findOwnRows(any(), any(), any())).thenReturn(List.of());
        when(plannedSessionRepository.findOwnRowsAfter(any(), any(), any())).thenReturn(List.of());

        var home = service.today();

        assertThat(home.sessions()).isEmpty();
        assertThat(home.next()).isNull();
        assertThat(home.last()).isNull();
    }

    @Test
    void aPastSessionWithoutValidationIsMissed() {
        UUID snapshotId = UUID.randomUUID();
        when(plannedSessionRepository.findOwnRows(athlete.getId(), TODAY.minusDays(7), TODAY))
                .thenReturn(List.of(row(TODAY.minusDays(2), snapshotId), row(TODAY, snapshotId)));
        when(snapshotExerciseRepository.countBySnapshot(any())).thenReturn(List.of());

        AthleteSessionListResponse response = service.calendar(TODAY.minusDays(7), TODAY);

        assertThat(response.sessions()).extracting(s -> s.status())
                .containsExactly(SessionStatus.MISSED, SessionStatus.UPCOMING);
        assertThat(response.sessions().get(0).exerciseCount()).isZero();
    }

    @Test
    void aValidatedSessionIsDoneEvenInThePastOrTheFuture() {
        UUID snapshotId = UUID.randomUUID();
        AthleteSessionRow past = row(TODAY.minusDays(2), snapshotId);
        AthleteSessionRow future = row(TODAY.plusDays(3), snapshotId);
        when(plannedSessionRepository.findOwnRows(athlete.getId(), TODAY.minusDays(7), TODAY.plusDays(7)))
                .thenReturn(List.of(past, future));
        when(snapshotExerciseRepository.countBySnapshot(any())).thenReturn(List.of());
        when(completion.summarize(any())).thenReturn(java.util.Map.of(past.id(), new CompletionSummary(null, 5, false),
                future.id(), new CompletionSummary(7, 5, false)));

        assertThat(service.calendar(TODAY.minusDays(7), TODAY.plusDays(7)).sessions())
                .extracting(s -> s.status()).containsOnly(SessionStatus.DONE);
    }

    @Test
    void refusesAnInvertedOrTooLongPeriodBeforeReadingAnything() {
        assertThatThrownBy(() -> service.calendar(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.calendar(TODAY, TODAY.plusDays(PlanningService.MAX_PERIOD_DAYS)))
                .isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(plannedSessionRepository);
    }

    @Test
    void anAthleteWithoutCoachReadsNothing() {
        doThrow(AthleteErrors.athleteWithoutCoach()).when(accessGuard).assertAthleteWithCoach();

        assertThatThrownBy(() -> service.today()).isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.calendar(TODAY, TODAY)).isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(plannedSessionRepository);
    }

    @Test
    void aSessionOfAnotherAthleteIsNotFound() {
        UUID id = UUID.randomUUID();
        when(plannedSessionRepository.findOwnById(id, athlete.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void detailKeepsPlannedExercisesAndSetsInOrder() {
        SessionSnapshot snapshot = withId(SessionSnapshot.builder().coach(coach).name("Haut du corps A")
                .description("Charges modérées").build(), UUID.randomUUID());
        SessionSnapshotExercise bench = SessionSnapshotExercise.builder().exerciseId(UUID.randomUUID())
                .exerciseName("Développé couché").muscleGroup(MuscleGroup.PECTORAUX).note("Pause en bas").build();
        bench.addSet(SessionSnapshotSet.builder().reps(8).loadKg(new BigDecimal("72.5")).restSeconds(120).build());
        bench.addSet(SessionSnapshotSet.builder().reps(6).loadKg(new BigDecimal("77.5")).build());
        snapshot.addExercise(bench);
        PlannedSession planned = withId(PlannedSession.builder().coach(coach).athlete(athlete)
                .scheduledDate(TODAY).snapshot(snapshot).build(), UUID.randomUUID());
        when(plannedSessionRepository.findOwnById(planned.getId(), athlete.getId())).thenReturn(Optional.of(planned));
        when(snapshotRepository.findDetailById(snapshot.getId())).thenReturn(Optional.of(snapshot));

        AthleteSessionDetailResponse detail = service.get(planned.getId());

        assertThat(detail.name()).isEqualTo("Haut du corps A");
        assertThat(detail.coachFirstName()).isEqualTo(coach.getFirstName());
        assertThat(detail.exercises()).singleElement().satisfies(exercise -> {
            assertThat(exercise.exerciseName()).isEqualTo("Développé couché");
            assertThat(exercise.note()).isEqualTo("Pause en bas");
            assertThat(exercise.sets()).extracting(set -> set.reps()).containsExactly(8, 6);
            assertThat(exercise.sets().get(0).loadKg()).isEqualByComparingTo("72.5");
            assertThat(exercise.sets().get(1).restSeconds()).isNull();
        });
        verify(snapshotExerciseRepository).fetchSetsOfSnapshot(snapshot.getId());
    }
}
