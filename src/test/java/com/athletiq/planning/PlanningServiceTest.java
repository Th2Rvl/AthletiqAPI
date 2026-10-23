package com.athletiq.planning;

import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.group.GroupErrors;
import com.athletiq.group.GroupMemberService;
import com.athletiq.group.dto.GroupMemberRef;
import com.athletiq.planning.dto.AthleteCalendarResponse;
import com.athletiq.planning.dto.CreatePlannedSessionRequest;
import com.athletiq.planning.dto.CreatedCountResponse;
import com.athletiq.planning.dto.DuplicateWeekRequest;
import com.athletiq.planning.dto.GroupCalendarResponse;
import com.athletiq.planning.dto.MovePlannedSessionRequest;
import com.athletiq.planning.dto.PlannedSessionDetailResponse;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserName;
import com.athletiq.user.UserService;
import com.athletiq.workout.WorkoutErrors;
import com.athletiq.workout.WorkoutService;
import com.athletiq.workout.dto.ExerciseRef;
import com.athletiq.workout.dto.PlannedSetResponse;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import com.athletiq.workout.dto.WorkoutResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Règles de la planification, sans base. L'horloge est fixée au 4 novembre 2026 à 23 h 30 UTC, soit le
 * 5 novembre à 0 h 30 à Paris : « aujourd'hui » est le 5 (Q12), et le 4 est déjà passé.
 */
@ExtendWith(MockitoExtension.class)
class PlanningServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T23:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    /** Lundi de la semaine courante. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Mock
    private PlannedSessionRepository plannedSessionRepository;
    @Mock
    private SessionSnapshotRepository snapshotRepository;
    @Mock
    private SessionSnapshotExerciseRepository snapshotExerciseRepository;
    @Mock
    private WorkoutService workoutService;
    @Mock
    private GroupMemberService groupMemberService;
    @Mock
    private UserService userService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private PlanningService service;
    private final UUID coachId = UUID.randomUUID();
    private final UUID athleteId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID workoutId = UUID.randomUUID();
    private final UUID squatId = UUID.randomUUID();
    private final UUID benchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PlanningClock clock = new PlanningClock(Clock.fixed(NOW, ZoneOffset.UTC),
                new PlanningProperties(PlanningProperties.DEFAULT_ZONE));
        service = new PlanningService(plannedSessionRepository, snapshotRepository, snapshotExerciseRepository,
                new PlanningMapperImpl(), workoutService, groupMemberService, userService, accessGuard, currentUser,
                clock);
        lenient().when(currentUser.id()).thenReturn(coachId);
        lenient().when(userService.getReference(any())).thenAnswer(call -> ref(call.getArgument(0)));
    }

    // ---------------------------------------------------------------- fabriques

    private static User ref(UUID id) {
        return withId(User.builder().email(id + "@club.fr").passwordHash("x").firstName("F").lastName("L")
                .role(Role.ATHLETE).build(), id);
    }

    /** « Haut du corps A » : développé couché (2 séries) en position 0, squat (1 série) en position 1. */
    private WorkoutResponse workout() {
        return new WorkoutResponse(workoutId, "Haut du corps A", "Consignes", List.of(
                new WorkoutExerciseResponse(1, new ExerciseRef(squatId, "Squat", MuscleGroup.QUADRICEPS), null,
                        List.of(new PlannedSetResponse(0, 5, new BigDecimal("80.0"), 150))),
                new WorkoutExerciseResponse(0, new ExerciseRef(benchId, "Développé couché", MuscleGroup.PECTORAUX),
                        "Tempo 3-1-1", List.of(new PlannedSetResponse(1, 6, null, null),
                        new PlannedSetResponse(0, 8, new BigDecimal("60.0"), 90)))), NOW, NOW);
    }

    private SessionSnapshot snapshot() {
        SessionSnapshot snapshot = SessionSnapshot.copyOf(ref(coachId), workout());
        withId(snapshot, UUID.randomUUID());
        return snapshot;
    }

    private PlannedSession plannedSession(LocalDate date) {
        return withId(PlannedSession.builder()
                .coach(ref(coachId))
                .athlete(ref(athleteId))
                .scheduledDate(date)
                .snapshot(snapshot())
                .sourceWorkoutId(workoutId)
                .build(), UUID.randomUUID());
    }

    private void stubOwn(PlannedSession plannedSession) {
        when(plannedSessionRepository.findByIdAndCoachId(plannedSession.getId(), coachId))
                .thenReturn(Optional.of(plannedSession));
    }

    private static PlannedSessionRow row(UUID athleteId, LocalDate date, UUID snapshotId, String name, UUID groupId) {
        return new PlannedSessionRow(UUID.randomUUID(), athleteId, date, snapshotId, name, groupId, null, NOW);
    }

    private void stubSavesWithIds() {
        when(snapshotRepository.save(any(SessionSnapshot.class)))
                .thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    private void assertNothingWritten() {
        verify(snapshotRepository, never()).save(any());
        verify(plannedSessionRepository, never()).saveAndFlush(any());
        verify(plannedSessionRepository, never()).saveAllAndFlush(anyList());
    }

    // ---------------------------------------------------------------- US21 planifier pour un athlète

    @Test
    void planningForAnAthleteCopiesTheWorkoutIntoANewSnapshotInOrder() {
        when(workoutService.get(workoutId)).thenReturn(workout());
        stubSavesWithIds();
        when(plannedSessionRepository.saveAndFlush(any(PlannedSession.class)))
                .thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));
        when(userService.findNames(List.of(athleteId))).thenReturn(Map.of(athleteId, new UserName("Lucas", "Girard")));

        PlannedSessionDetailResponse detail = service.planForAthlete(athleteId,
                new CreatePlannedSessionRequest(workoutId, TODAY));

        InOrder order = inOrder(accessGuard, workoutService, snapshotRepository);
        order.verify(accessGuard).assertCoachOfAthlete(athleteId);
        order.verify(workoutService).get(workoutId);
        order.verify(snapshotRepository).save(any());
        ArgumentCaptor<PlannedSession> saved = ArgumentCaptor.forClass(PlannedSession.class);
        verify(plannedSessionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getScheduledDate()).isEqualTo(TODAY);
        assertThat(saved.getValue().getAthlete().getId()).isEqualTo(athleteId);
        assertThat(saved.getValue().getCoach().getId()).isEqualTo(coachId);
        assertThat(saved.getValue().getGroupId()).isNull();
        assertThat(saved.getValue().getSourceWorkoutId()).isEqualTo(workoutId);

        SessionSnapshot snapshot = saved.getValue().getSnapshot();
        assertThat(snapshot.getName()).isEqualTo("Haut du corps A");
        assertThat(snapshot.getDescription()).isEqualTo("Consignes");
        assertThat(snapshot.getExercises()).extracting(SessionSnapshotExercise::getExerciseName)
                .containsExactly("Développé couché", "Squat");
        assertThat(snapshot.getExercises().getFirst().getSets()).extracting(SessionSnapshotSet::getReps)
                .containsExactly(8, 6);

        assertThat(detail.date()).isEqualTo(TODAY);
        assertThat(detail.locked()).isFalse();
        assertThat(detail.athleteFirstName()).isEqualTo("Lucas");
        assertThat(detail.athleteLastName()).isEqualTo("Girard");
        assertThat(detail.exercises()).extracting(exercise -> exercise.exercise().name())
                .containsExactly("Développé couché", "Squat");
        assertThat(detail.exercises().getFirst().exercise().muscleGroup()).isEqualTo(MuscleGroup.PECTORAUX);
        assertThat(detail.exercises().getFirst().note()).isEqualTo("Tempo 3-1-1");
        assertThat(detail.exercises().getFirst().sets()).extracting(PlannedSetResponse::loadKg)
                .containsExactly(new BigDecimal("60.0"), null);
    }

    @Test
    void aDateBeforeTodayInParisIsRefusedEvenIfItIsStillTodayInUtc() {
        when(workoutService.get(workoutId)).thenReturn(workout());

        Throwable thrown = catchThrowable(() -> service.planForAthlete(athleteId,
                new CreatePlannedSessionRequest(workoutId, YESTERDAY)));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.PLANNING_DATE_IN_PAST);
        assertThat(((DomainException) thrown).getField()).isEqualTo("date");
        assertNothingWritten();
    }

    @Test
    void planningForAnAthleteNotFollowedCreatesNothing() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.planForAthlete(athleteId,
                new CreatePlannedSessionRequest(workoutId, TODAY)));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(workoutService);
        assertNothingWritten();
    }

    @Test
    void planningTheWorkoutOfAnotherCoachCreatesNothing() {
        when(workoutService.get(workoutId)).thenThrow(WorkoutErrors.workoutNotFound());

        Throwable thrown = catchThrowable(() -> service.planForAthlete(athleteId,
                new CreatePlannedSessionRequest(workoutId, TODAY)));

        assertDomainError(thrown, ResourceNotFoundException.class, WorkoutErrors.WORKOUT_NOT_FOUND);
        assertNothingWritten();
    }

    // ---------------------------------------------------------------- US22 planifier pour un groupe

    private List<GroupMemberRef> members(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new GroupMemberRef(UUID.randomUUID(), "Prénom" + i, "Nom" + i))
                .toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void planningForAGroupCreatesOneSessionPerCurrentMemberSharingOneSnapshot() {
        List<GroupMemberRef> members = members(3);
        when(workoutService.get(workoutId)).thenReturn(workout());
        when(groupMemberService.listAllMembers(groupId)).thenReturn(members);
        stubSavesWithIds();

        CreatedCountResponse created = service.planForGroup(groupId, new CreatePlannedSessionRequest(workoutId, TODAY));

        assertThat(created.created()).isEqualTo(3);
        verify(accessGuard).assertGroupOwner(groupId);
        verify(accessGuard).assertCoachOfAthletes(members.stream().map(GroupMemberRef::athleteId).toList());
        verify(snapshotRepository).save(any());
        ArgumentCaptor<List<PlannedSession>> saved = ArgumentCaptor.forClass(List.class);
        verify(plannedSessionRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).hasSize(3)
                .allSatisfy(session -> {
                    assertThat(session.getGroupId()).isEqualTo(groupId);
                    assertThat(session.getScheduledDate()).isEqualTo(TODAY);
                    assertThat(session.getSnapshot()).isSameAs(saved.getValue().getFirst().getSnapshot());
                });
        assertThat(saved.getValue()).extracting(session -> session.getAthlete().getId())
                .containsExactlyElementsOf(members.stream().map(GroupMemberRef::athleteId).toList());
    }

    @Test
    void planningForAnEmptyGroupIsRefused() {
        when(workoutService.get(workoutId)).thenReturn(workout());
        when(groupMemberService.listAllMembers(groupId)).thenReturn(List.of());

        Throwable thrown = catchThrowable(() -> service.planForGroup(groupId,
                new CreatePlannedSessionRequest(workoutId, TODAY)));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.GROUP_HAS_NO_MEMBERS);
        assertNothingWritten();
    }

    @Test
    void planningForAGroupInThePastIsRefused() {
        when(workoutService.get(workoutId)).thenReturn(workout());
        when(groupMemberService.listAllMembers(groupId)).thenReturn(members(2));

        Throwable thrown = catchThrowable(() -> service.planForGroup(groupId,
                new CreatePlannedSessionRequest(workoutId, YESTERDAY)));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.PLANNING_DATE_IN_PAST);
        assertNothingWritten();
    }

    @Test
    void planningForTheGroupOfAnotherCoachCreatesNothing() {
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(groupId);

        Throwable thrown = catchThrowable(() -> service.planForGroup(groupId,
                new CreatePlannedSessionRequest(workoutId, TODAY)));

        assertDomainError(thrown, ResourceNotFoundException.class, GroupErrors.GROUP_NOT_FOUND);
        verifyNoInteractions(workoutService, groupMemberService);
        assertNothingWritten();
    }

    @Test
    void planningForAHugeGroupIsRefusedAsABatchTooLarge() {
        when(workoutService.get(workoutId)).thenReturn(workout());
        when(groupMemberService.listAllMembers(groupId)).thenReturn(members(PlanningService.MAX_BATCH_SIZE + 1));

        Throwable thrown = catchThrowable(() -> service.planForGroup(groupId,
                new CreatePlannedSessionRequest(workoutId, TODAY)));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.PLANNING_BATCH_TOO_LARGE);
        assertNothingWritten();
    }

    // ---------------------------------------------------------------- US24 calendriers

    @Test
    void thePeriodIsCheckedBeforeAnyLookup() {
        Throwable reversed = catchThrowable(() -> service.athleteCalendar(athleteId, TODAY, YESTERDAY));
        Throwable tooLong = catchThrowable(() -> service.groupCalendar(groupId, MONDAY, MONDAY.plusDays(84)));

        assertDomainError(reversed, InvalidParameterException.class, ErrorCodes.INVALID_PARAMETER);
        assertDomainError(tooLong, InvalidParameterException.class, ErrorCodes.INVALID_PARAMETER);
        verifyNoInteractions(accessGuard, plannedSessionRepository);
    }

    @Test
    void theAthleteCalendarListsOnlyTheSessionsOfThisCoachWithCountsAndLocks() {
        UUID upper = UUID.randomUUID();
        UUID lower = UUID.randomUUID();
        LocalDate to = MONDAY.plusDays(83);
        List<PlannedSessionRow> rows = List.of(
                row(athleteId, YESTERDAY, lower, "Bas du corps A", null),
                row(athleteId, TODAY, upper, "Haut du corps A", groupId),
                row(athleteId, TODAY, lower, "Bas du corps A", null));
        when(plannedSessionRepository.findAthleteRows(coachId, athleteId, MONDAY, to)).thenReturn(rows);
        when(snapshotExerciseRepository.countBySnapshot(anyCollection())).thenReturn(List.of(
                new SnapshotCount(upper, 2, 3), new SnapshotCount(lower, 1, 5)));

        AthleteCalendarResponse calendar = service.athleteCalendar(athleteId, MONDAY, to);

        verify(accessGuard).assertCoachOfAthlete(athleteId);
        assertThat(calendar.today()).isEqualTo(TODAY);
        assertThat(calendar.from()).isEqualTo(MONDAY);
        assertThat(calendar.to()).isEqualTo(to);
        assertThat(calendar.sessions()).hasSize(3);
        assertThat(calendar.sessions()).extracting(summary -> summary.locked()).containsExactly(true, false, false);
        assertThat(calendar.sessions().get(1).exerciseCount()).isEqualTo(2);
        assertThat(calendar.sessions().get(1).setCount()).isEqualTo(3);
        assertThat(calendar.sessions().get(1).groupId()).isEqualTo(groupId);
        assertThat(calendar.sessions().get(2).name()).isEqualTo("Bas du corps A");
    }

    @Test
    void theCalendarOfAnAthleteNotFollowedAnswers404() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.athleteCalendar(athleteId, MONDAY, MONDAY.plusDays(27)));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(plannedSessionRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theGroupCalendarGroupsByDayAndSnapshotForCurrentMembers() {
        GroupMemberRef durand = new GroupMemberRef(UUID.randomUUID(), "Lucas", "Durand");
        GroupMemberRef martin = new GroupMemberRef(UUID.randomUUID(), "Emma", "Martin");
        GroupMemberRef petit = new GroupMemberRef(UUID.randomUUID(), "Hugo", "Petit");
        when(groupMemberService.listAllMembers(groupId)).thenReturn(List.of(durand, martin, petit));
        UUID upper = UUID.randomUUID();
        UUID lower = UUID.randomUUID();
        LocalDate later = TODAY.plusDays(7);
        when(plannedSessionRepository.findGroupRows(any(), any(), anyCollection(), any(), any())).thenReturn(List.of(
                row(martin.athleteId(), TODAY, upper, "Haut du corps A", groupId),
                row(durand.athleteId(), TODAY, upper, "Haut du corps A", groupId),
                row(petit.athleteId(), TODAY, lower, "Bas du corps A", groupId),
                row(durand.athleteId(), later, upper, "Haut du corps A", groupId)));

        GroupCalendarResponse calendar = service.groupCalendar(groupId, MONDAY, MONDAY.plusDays(27));

        ArgumentCaptor<Collection<UUID>> athleteIds = ArgumentCaptor.forClass(Collection.class);
        verify(plannedSessionRepository).findGroupRows(org.mockito.ArgumentMatchers.eq(coachId),
                org.mockito.ArgumentMatchers.eq(groupId), athleteIds.capture(),
                org.mockito.ArgumentMatchers.eq(MONDAY), org.mockito.ArgumentMatchers.eq(MONDAY.plusDays(27)));
        assertThat(athleteIds.getValue()).containsExactlyInAnyOrder(durand.athleteId(), martin.athleteId(),
                petit.athleteId());
        assertThat(calendar.memberCount()).isEqualTo(3);
        assertThat(calendar.entries()).extracting(entry -> entry.name() + "@" + entry.date())
                .containsExactly("Bas du corps A@" + TODAY, "Haut du corps A@" + TODAY, "Haut du corps A@" + later);
        var upperToday = calendar.entries().get(1);
        assertThat(upperToday.athleteCount()).isEqualTo(2);
        assertThat(upperToday.memberCount()).isEqualTo(3);
        assertThat(upperToday.locked()).isFalse();
        assertThat(upperToday.sessions()).extracting(session -> session.lastName()).containsExactly("Durand", "Martin");
    }

    @Test
    void theCalendarOfAGroupWithoutMembersIsEmpty() {
        when(groupMemberService.listAllMembers(groupId)).thenReturn(List.of());

        GroupCalendarResponse calendar = service.groupCalendar(groupId, MONDAY, MONDAY.plusDays(6));

        assertThat(calendar.entries()).isEmpty();
        assertThat(calendar.memberCount()).isZero();
        verifyNoInteractions(plannedSessionRepository);
    }

    @Test
    void theCalendarOfTheGroupOfAnotherCoachAnswers404() {
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(groupId);

        Throwable thrown = catchThrowable(() -> service.groupCalendar(groupId, MONDAY, MONDAY.plusDays(6)));

        assertDomainError(thrown, ResourceNotFoundException.class, GroupErrors.GROUP_NOT_FOUND);
        verifyNoInteractions(groupMemberService, plannedSessionRepository);
    }

    @Test
    void theUpcomingCountStartsToday() {
        when(plannedSessionRepository.countByCoachIdAndAthleteIdAndScheduledDateGreaterThanEqual(coachId, athleteId,
                TODAY)).thenReturn(4L);

        assertThat(service.upcomingCount(athleteId).count()).isEqualTo(4);
        verify(accessGuard).assertCoachOfAthlete(athleteId);
    }

    @Test
    void theUpcomingCountOfAnAthleteNotFollowedAnswers404() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.upcomingCount(athleteId));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(plannedSessionRepository);
    }

    // ---------------------------------------------------------------- détail, déplacement, annulation

    @Test
    void theDetailOfAPlannedSessionOfAnotherCoachAnswers404() {
        UUID foreign = UUID.randomUUID();
        doThrow(PlanningErrors.plannedSessionNotFound()).when(accessGuard).assertPlannedSessionOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.get(foreign));

        assertDomainError(thrown, ResourceNotFoundException.class, PlanningErrors.PLANNED_SESSION_NOT_FOUND);
        verifyNoInteractions(plannedSessionRepository);
    }

    @Test
    void theDetailComesFromTheSnapshotAndAPastSessionIsLocked() {
        PlannedSession past = plannedSession(YESTERDAY);
        stubOwn(past);
        when(snapshotRepository.findDetailById(past.getSnapshot().getId())).thenReturn(Optional.of(past.getSnapshot()));

        PlannedSessionDetailResponse detail = service.get(past.getId());

        verify(accessGuard).assertPlannedSessionOwner(past.getId());
        verify(snapshotExerciseRepository).fetchSetsOfSnapshot(past.getSnapshot().getId());
        assertThat(detail.locked()).isTrue();
        assertThat(detail.name()).isEqualTo("Haut du corps A");
        assertThat(detail.sourceWorkoutId()).isEqualTo(workoutId);
    }

    @Test
    void movingChangesOnlyTheDate() {
        PlannedSession session = plannedSession(TODAY);
        stubOwn(session);
        when(snapshotRepository.findDetailById(any())).thenReturn(Optional.of(session.getSnapshot()));

        PlannedSessionDetailResponse moved = service.move(session.getId(), new MovePlannedSessionRequest(TODAY.plusDays(3)));

        assertThat(session.getScheduledDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(moved.date()).isEqualTo(TODAY.plusDays(3));
        assertThat(moved.name()).isEqualTo("Haut du corps A");
        verify(plannedSessionRepository).flush();
    }

    @Test
    void movingToTheSameDateChangesNothing() {
        PlannedSession session = plannedSession(TODAY);
        stubOwn(session);
        when(snapshotRepository.findDetailById(any())).thenReturn(Optional.of(session.getSnapshot()));

        service.move(session.getId(), new MovePlannedSessionRequest(TODAY));

        verify(plannedSessionRepository, never()).flush();
    }

    @Test
    void aPastSessionCannotBeMovedNorCancelled() {
        PlannedSession past = plannedSession(YESTERDAY);
        stubOwn(past);

        Throwable moved = catchThrowable(() -> service.move(past.getId(), new MovePlannedSessionRequest(TODAY)));
        Throwable cancelled = catchThrowable(() -> service.cancel(past.getId()));

        assertDomainError(moved, BusinessRuleException.class, PlanningErrors.PLANNED_SESSION_LOCKED);
        assertDomainError(cancelled, BusinessRuleException.class, PlanningErrors.PLANNED_SESSION_LOCKED);
        assertThat(past.getScheduledDate()).isEqualTo(YESTERDAY);
        verify(plannedSessionRepository, never()).deleteByIdAndCoachId(any(), any());
    }

    @Test
    void movingToAPastDateIsRefused() {
        PlannedSession session = plannedSession(TODAY.plusDays(2));
        stubOwn(session);

        Throwable thrown = catchThrowable(() -> service.move(session.getId(), new MovePlannedSessionRequest(YESTERDAY)));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.PLANNING_DATE_IN_PAST);
        assertThat(((DomainException) thrown).getField()).isEqualTo("date");
        assertThat(session.getScheduledDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    void movingOrCancellingThePlannedSessionOfAnotherCoachAnswers404() {
        UUID foreign = UUID.randomUUID();
        doThrow(PlanningErrors.plannedSessionNotFound()).when(accessGuard).assertPlannedSessionOwner(foreign);

        Throwable moved = catchThrowable(() -> service.move(foreign, new MovePlannedSessionRequest(TODAY)));
        Throwable cancelled = catchThrowable(() -> service.cancel(foreign));

        assertDomainError(moved, ResourceNotFoundException.class, PlanningErrors.PLANNED_SESSION_NOT_FOUND);
        assertDomainError(cancelled, ResourceNotFoundException.class, PlanningErrors.PLANNED_SESSION_NOT_FOUND);
        verifyNoInteractions(plannedSessionRepository, snapshotRepository);
    }

    @Test
    void cancellingDeletesTheSessionThenItsSnapshotIfOrphan() {
        PlannedSession session = plannedSession(TODAY);
        stubOwn(session);
        when(plannedSessionRepository.deleteByIdAndCoachId(session.getId(), coachId)).thenReturn(1);

        service.cancel(session.getId());

        InOrder order = inOrder(plannedSessionRepository, snapshotRepository);
        order.verify(plannedSessionRepository).deleteByIdAndCoachId(session.getId(), coachId);
        order.verify(snapshotRepository).deleteOrphans(List.of(session.getSnapshot().getId()));
    }

    // ---------------------------------------------------------------- US26 dupliquer une semaine

    @Test
    @SuppressWarnings("unchecked")
    void duplicatingAnAthleteWeekReusesTheSameSnapshots() {
        UUID upper = UUID.randomUUID();
        UUID lower = UUID.randomUUID();
        PlannedSessionRow monday = row(athleteId, MONDAY, upper, "Haut du corps A", groupId);
        PlannedSessionRow friday = row(athleteId, MONDAY.plusDays(4), lower, "Bas du corps A", null);
        when(plannedSessionRepository.findAthleteRows(coachId, athleteId, MONDAY, MONDAY.plusDays(6)))
                .thenReturn(List.of(monday, friday));
        when(snapshotRepository.getReferenceById(any())).thenAnswer(call -> withId(
                SessionSnapshot.builder().name("ref").build(), call.getArgument(0)));

        CreatedCountResponse created = service.duplicateWeekForAthlete(athleteId, new DuplicateWeekRequest(MONDAY, 3));

        assertThat(created.created()).isEqualTo(6);
        ArgumentCaptor<List<PlannedSession>> saved = ArgumentCaptor.forClass(List.class);
        verify(plannedSessionRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).extracting(PlannedSession::getScheduledDate).containsExactly(
                MONDAY.plusWeeks(1), MONDAY.plusDays(4).plusWeeks(1),
                MONDAY.plusWeeks(2), MONDAY.plusDays(4).plusWeeks(2),
                MONDAY.plusWeeks(3), MONDAY.plusDays(4).plusWeeks(3));
        assertThat(saved.getValue()).extracting(session -> session.getSnapshot().getId())
                .containsExactly(upper, lower, upper, lower, upper, lower);
        assertThat(saved.getValue().getFirst().getGroupId()).isEqualTo(groupId);
        assertThat(saved.getValue().get(1).getGroupId()).isNull();
        verify(snapshotRepository, never()).save(any());
        verifyNoInteractions(workoutService);
    }

    @Test
    void duplicatingAnEmptyWeekIsRefused() {
        when(plannedSessionRepository.findAthleteRows(any(), any(), any(), any())).thenReturn(List.of());

        Throwable thrown = catchThrowable(() -> service.duplicateWeekForAthlete(athleteId,
                new DuplicateWeekRequest(MONDAY, 1)));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.PLANNING_WEEK_EMPTY);
        assertNothingWritten();
    }

    @Test
    void aPastSourceWeekIsAllowedButNotAPastTarget() {
        LocalDate twoWeeksAgo = MONDAY.minusWeeks(2);
        // Mardi d'il y a deux semaines : la première cible (+1 semaine) est passée.
        when(plannedSessionRepository.findAthleteRows(coachId, athleteId, twoWeeksAgo, twoWeeksAgo.plusDays(6)))
                .thenReturn(List.of(row(athleteId, twoWeeksAgo.plusDays(1), UUID.randomUUID(), "A", null)));
        // Jeudi de la semaine dernière : la cible est jeudi 5, aujourd'hui, donc permise.
        LocalDate lastWeek = MONDAY.minusWeeks(1);
        when(plannedSessionRepository.findAthleteRows(coachId, athleteId, lastWeek, lastWeek.plusDays(6)))
                .thenReturn(List.of(row(athleteId, lastWeek.plusDays(3), UUID.randomUUID(), "A", null)));

        Throwable thrown = catchThrowable(() -> service.duplicateWeekForAthlete(athleteId,
                new DuplicateWeekRequest(twoWeeksAgo, 2)));
        CreatedCountResponse created = service.duplicateWeekForAthlete(athleteId, new DuplicateWeekRequest(lastWeek, 1));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.PLANNING_DATE_IN_PAST);
        assertThat(((DomainException) thrown).getField()).isNull();
        assertThat(created.created()).isEqualTo(1);
    }

    @Test
    void duplicatingTooManySessionsIsRefused() {
        List<PlannedSessionRow> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(row(athleteId, MONDAY.plusDays(i % 7), UUID.randomUUID(), "A", null));
        }
        when(plannedSessionRepository.findAthleteRows(any(), any(), any(), any())).thenReturn(rows);

        Throwable thrown = catchThrowable(() -> service.duplicateWeekForAthlete(athleteId,
                new DuplicateWeekRequest(MONDAY, 11)));

        assertDomainError(thrown, BusinessRuleException.class, PlanningErrors.PLANNING_BATCH_TOO_LARGE);
        assertNothingWritten();
    }

    @Test
    void duplicatingTheWeekOfAnAthleteNotFollowedCreatesNothing() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.duplicateWeekForAthlete(athleteId,
                new DuplicateWeekRequest(MONDAY, 1)));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(plannedSessionRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicatingAGroupWeekCopiesEachDayAndSnapshotOnceForEachCurrentMember() {
        List<GroupMemberRef> members = members(3);
        when(groupMemberService.listAllMembers(groupId)).thenReturn(members);
        UUID upper = UUID.randomUUID();
        UUID lower = UUID.randomUUID();
        UUID formerMember = UUID.randomUUID();
        when(plannedSessionRepository.findGroupSourceRows(coachId, groupId, MONDAY, MONDAY.plusDays(6))).thenReturn(List.of(
                row(formerMember, MONDAY.plusDays(1), upper, "Haut du corps A", groupId),
                row(members.getFirst().athleteId(), MONDAY.plusDays(1), upper, "Haut du corps A", groupId),
                row(members.get(1).athleteId(), MONDAY.plusDays(3), lower, "Bas du corps A", groupId)));
        when(snapshotRepository.getReferenceById(any())).thenAnswer(call -> withId(
                SessionSnapshot.builder().name("ref").build(), call.getArgument(0)));

        CreatedCountResponse created = service.duplicateWeekForGroup(groupId, new DuplicateWeekRequest(MONDAY, 2));

        assertThat(created.created()).isEqualTo(2 * 3 * 2);
        verify(accessGuard).assertCoachOfAthletes(members.stream().map(GroupMemberRef::athleteId).toList());
        ArgumentCaptor<List<PlannedSession>> saved = ArgumentCaptor.forClass(List.class);
        verify(plannedSessionRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).allSatisfy(session -> assertThat(session.getGroupId()).isEqualTo(groupId));
        assertThat(saved.getValue()).extracting(session -> session.getAthlete().getId())
                .doesNotContain(formerMember);
        assertThat(saved.getValue()).filteredOn(session -> session.getSnapshot().getId().equals(upper))
                .extracting(PlannedSession::getScheduledDate)
                .containsOnly(MONDAY.plusDays(1).plusWeeks(1), MONDAY.plusDays(1).plusWeeks(2));
    }

    @Test
    void duplicatingTheWeekOfAnEmptyGroupOrAnEmptyGroupWeekIsRefused() {
        UUID emptyGroup = UUID.randomUUID();
        when(groupMemberService.listAllMembers(emptyGroup)).thenReturn(List.of());
        when(groupMemberService.listAllMembers(groupId)).thenReturn(members(2));
        when(plannedSessionRepository.findGroupSourceRows(any(), any(), any(), any())).thenReturn(List.of());

        Throwable noMembers = catchThrowable(() -> service.duplicateWeekForGroup(emptyGroup,
                new DuplicateWeekRequest(MONDAY, 1)));
        Throwable noSessions = catchThrowable(() -> service.duplicateWeekForGroup(groupId,
                new DuplicateWeekRequest(MONDAY, 1)));

        assertDomainError(noMembers, BusinessRuleException.class, PlanningErrors.GROUP_HAS_NO_MEMBERS);
        assertDomainError(noSessions, BusinessRuleException.class, PlanningErrors.PLANNING_WEEK_EMPTY);
        assertNothingWritten();
    }

    @Test
    void duplicatingTheWeekOfTheGroupOfAnotherCoachCreatesNothing() {
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(groupId);

        Throwable thrown = catchThrowable(() -> service.duplicateWeekForGroup(groupId,
                new DuplicateWeekRequest(MONDAY, 1)));

        assertDomainError(thrown, ResourceNotFoundException.class, GroupErrors.GROUP_NOT_FOUND);
        verifyNoInteractions(groupMemberService, plannedSessionRepository);
    }
}
