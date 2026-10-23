package com.athletiq.workout;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.Exercise;
import com.athletiq.exercise.ExerciseErrors;
import com.athletiq.exercise.ExerciseService;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import com.athletiq.workout.dto.PlannedSetRequest;
import com.athletiq.workout.dto.PlannedSetResponse;
import com.athletiq.workout.dto.WorkoutExerciseRequest;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import com.athletiq.workout.dto.WorkoutRequest;
import com.athletiq.workout.dto.WorkoutResponse;
import com.athletiq.workout.dto.WorkoutSummaryResponse;
import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Mock
    private ExerciseService exerciseService;
    @Mock
    private UserService userService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private WorkoutService service;
    private User coach;
    private Exercise squat;
    private Exercise bench;

    @BeforeEach
    void setUp() {
        service = new WorkoutService(workoutRepository, workoutExerciseRepository, new WorkoutMapperImpl(),
                exerciseService, userService, accessGuard, currentUser);
        coach = user("coach@club.fr", Role.COACH);
        squat = exercise("Squat", MuscleGroup.QUADRICEPS);
        bench = exercise("Développé couché", MuscleGroup.PECTORAUX);
    }

    private Exercise exercise(String name, MuscleGroup muscleGroup) {
        return withId(Exercise.builder().coach(coach).name(name).muscleGroup(muscleGroup).build(), UUID.randomUUID());
    }

    private static PlannedSetRequest set(int reps, String loadKg, Integer restSeconds) {
        return new PlannedSetRequest(reps, loadKg == null ? null : new BigDecimal(loadKg), restSeconds);
    }

    private WorkoutRequest request(String name) {
        return new WorkoutRequest(name, "  ", List.of(
                new WorkoutExerciseRequest(squat.getId(), "Tempo 3-1-1",
                        List.of(set(8, "60", 90), set(6, "62.5", null))),
                new WorkoutExerciseRequest(bench.getId(), null, List.of(set(10, null, 60)))));
    }

    /** Séance persistée « Haut du corps A » : squat (2 séries), puis développé couché (1 série). */
    private Workout savedWorkout(String name) {
        Workout workout = withId(Workout.builder().coach(coach).name(name).description("Consignes").build(),
                UUID.randomUUID());
        WorkoutExercise first = withId(WorkoutExercise.builder().exercise(squat).note("Tempo").build(),
                UUID.randomUUID());
        first.addSet(withId(PlannedSet.builder().reps(8).loadKg(new BigDecimal("60.0")).restSeconds(90).build(),
                UUID.randomUUID()));
        first.addSet(withId(PlannedSet.builder().reps(6).loadKg(new BigDecimal("62.5")).build(), UUID.randomUUID()));
        WorkoutExercise second = withId(WorkoutExercise.builder().exercise(bench).build(), UUID.randomUUID());
        second.addSet(withId(PlannedSet.builder().reps(10).restSeconds(60).build(), UUID.randomUUID()));
        workout.addExercise(first);
        workout.addExercise(second);
        return workout;
    }

    private void stubDetail(Workout workout) {
        when(workoutRepository.findDetailByIdAndCoachId(workout.getId(), coach.getId()))
                .thenReturn(Optional.of(workout));
    }

    private void stubExercisesOfCoach() {
        when(exerciseService.findOwned(any())).thenReturn(Map.of(squat.getId(), squat, bench.getId(), bench));
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    private static DataIntegrityViolationException duplicateNameViolation() {
        return new DataIntegrityViolationException("doublon", new SQLIntegrityConstraintViolationException(
                "Duplicate entry for key 'workout.uk_workout_coach_name'"));
    }

    // ---------------------------------------------------------------- liste

    @Test
    void listsTheWorkoutsOfTheCoachWithTheirExerciseCountAndMuscleGroupsInTwoQueries() {
        when(currentUser.id()).thenReturn(coach.getId());
        Workout upper = savedWorkout("Haut du corps A");
        Workout empty = withId(Workout.builder().coach(coach).name("Vide").build(), UUID.randomUUID());
        when(workoutRepository.search(eq(coach.getId()), eq("%haut%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(upper, empty)));
        when(workoutExerciseRepository.countByMuscleGroup(List.of(upper.getId(), empty.getId()))).thenReturn(List.of(
                new WorkoutMuscleGroupCount(upper.getId(), MuscleGroup.QUADRICEPS, 2),
                new WorkoutMuscleGroupCount(upper.getId(), MuscleGroup.PECTORAUX, 1)));

        List<WorkoutSummaryResponse> content = service.list(" haut ", PageRequest.of(0, 20)).getContent();

        assertThat(content).extracting(WorkoutSummaryResponse::name).containsExactly("Haut du corps A", "Vide");
        assertThat(content.get(0).exerciseCount()).isEqualTo(3);
        assertThat(content.get(0).muscleGroups()).containsExactly(MuscleGroup.PECTORAUX, MuscleGroup.QUADRICEPS);
        assertThat(content.get(0).description()).isEqualTo("Consignes");
        assertThat(content.get(1).exerciseCount()).isZero();
        assertThat(content.get(1).muscleGroups()).isEmpty();
        verify(accessGuard).assertCoach();
    }

    @Test
    void aBlankSearchMeansNoFilterAndAnEmptyPageMakesNoAggregation() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(workoutRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list("   ", PageRequest.of(0, 20));

        verify(workoutRepository).search(eq(coach.getId()), eq(null), any(Pageable.class));
        verifyNoInteractions(workoutExerciseRepository);
    }

    @Test
    void theListIgnoresAnySortRequestedByTheCaller() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(workoutRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(null, PageRequest.of(1, 5, org.springframework.data.domain.Sort.by("name")));

        verify(workoutRepository).search(coach.getId(), null, PageRequest.of(1, 5));
    }

    @Test
    void aNonCoachIsRefusedBeforeAnyQuery() {
        doThrow(new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé")).when(accessGuard).assertCoach();

        Throwable thrown = catchThrowable(() -> service.list(null, PageRequest.of(0, 20)));

        assertDomainError(thrown, ForbiddenActionException.class, ErrorCodes.ACCESS_DENIED);
        verifyNoInteractions(workoutRepository, workoutExerciseRepository);
    }

    // ---------------------------------------------------------------- détail

    @Test
    void readsAWorkoutWithItsExercisesAndSetsSortedByPositionInTwoQueries() {
        when(currentUser.id()).thenReturn(coach.getId());
        Workout workout = savedWorkout("Haut du corps A");
        // Chargées dans le désordre : la réponse reste triée par position.
        workout.getExercises().get(0).getSets().add(0, workout.getExercises().get(0).getSets().remove(1));
        workout.getExercises().add(0, workout.getExercises().remove(1));
        stubDetail(workout);

        WorkoutResponse read = service.get(workout.getId());

        assertThat(read.name()).isEqualTo("Haut du corps A");
        assertThat(read.exercises()).extracting(WorkoutExerciseResponse::position).containsExactly(0, 1);
        assertThat(read.exercises().get(0).exercise().name()).isEqualTo("Squat");
        assertThat(read.exercises().get(0).exercise().muscleGroup()).isEqualTo(MuscleGroup.QUADRICEPS);
        assertThat(read.exercises().get(0).sets()).extracting(PlannedSetResponse::position, PlannedSetResponse::reps)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0, 8), org.assertj.core.groups.Tuple.tuple(1, 6));
        assertThat(read.exercises().get(1).sets().get(0).loadKg()).isNull();
        verify(accessGuard).assertWorkoutOwner(workout.getId());
        verify(workoutExerciseRepository).fetchSetsOfWorkout(workout.getId());
    }

    @Test
    void theWorkoutOfAnotherCoachAnswers404AndIsNotRead() {
        UUID foreign = UUID.randomUUID();
        doThrow(WorkoutErrors.workoutNotFound()).when(accessGuard).assertWorkoutOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.get(foreign));

        assertDomainError(thrown, ResourceNotFoundException.class, WorkoutErrors.WORKOUT_NOT_FOUND);
        verifyNoInteractions(workoutRepository, workoutExerciseRepository);
    }

    @Test
    void theRepositoryStillFiltersByCoachIfTheGuardLetsAnUnknownIdThrough() {
        when(currentUser.id()).thenReturn(coach.getId());
        UUID unknown = UUID.randomUUID();
        when(workoutRepository.findDetailByIdAndCoachId(unknown, coach.getId())).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.get(unknown));

        assertDomainError(thrown, ResourceNotFoundException.class, WorkoutErrors.WORKOUT_NOT_FOUND);
    }

    // ---------------------------------------------------------------- création

    @Test
    void createsAWorkoutWithItsExercisesAndSetsInTheOrderReceived() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        stubExercisesOfCoach();
        when(workoutRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        WorkoutResponse created = service.create(request("Haut du corps A"));

        ArgumentCaptor<Workout> saved = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).saveAndFlush(saved.capture());
        Workout workout = saved.getValue();
        assertThat(workout.getCoach()).isSameAs(coach);
        assertThat(workout.getDescription()).isNull();
        assertThat(workout.getExercises()).extracting(WorkoutExercise::getExercise).containsExactly(squat, bench);
        assertThat(workout.getExercises()).extracting(WorkoutExercise::getPosition).containsExactly(0, 1);
        assertThat(workout.getExercises()).allSatisfy(e -> assertThat(e.getWorkout()).isSameAs(workout));
        List<PlannedSet> sets = workout.getExercises().get(0).getSets();
        assertThat(sets).extracting(PlannedSet::getPosition).containsExactly(0, 1);
        assertThat(sets).extracting(PlannedSet::getLoadKg)
                .containsExactly(new BigDecimal("60.0"), new BigDecimal("62.5"));
        assertThat(sets.get(1).getRestSeconds()).isNull();
        assertThat(created.exercises().get(0).note()).isEqualTo("Tempo 3-1-1");
        assertThat(created.exercises().get(1).sets().get(0).loadKg()).isNull();

        InOrder order = inOrder(accessGuard, workoutRepository);
        order.verify(accessGuard).assertCoach();
        order.verify(accessGuard).assertExercisesOwner(List.of(squat.getId(), bench.getId()));
        order.verify(workoutRepository).existsByCoachIdAndName(coach.getId(), "Haut du corps A");
    }

    @Test
    void theSameExerciseMayAppearTwice() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        when(exerciseService.findOwned(any())).thenReturn(Map.of(squat.getId(), squat));
        when(workoutRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));
        WorkoutRequest twice = new WorkoutRequest("Jambes", null, List.of(
                new WorkoutExerciseRequest(squat.getId(), "Échauffement", List.of(set(10, "40", 60))),
                new WorkoutExerciseRequest(squat.getId(), null, List.of(set(5, "100", 180)))));

        WorkoutResponse created = service.create(twice);

        assertThat(created.exercises()).extracting(e -> e.exercise().id()).containsExactly(squat.getId(), squat.getId());
        verify(accessGuard).assertExercisesOwner(List.of(squat.getId(), squat.getId()));
    }

    @Test
    void anExerciseOfAnotherCoachRefusesTheCreationAndWritesNothing() {
        doThrow(ExerciseErrors.exerciseNotFound()).when(accessGuard).assertExercisesOwner(any());

        Throwable thrown = catchThrowable(() -> service.create(request("Haut du corps A")));

        assertDomainError(thrown, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
        verifyNoInteractions(workoutRepository, exerciseService);
    }

    @Test
    void anExerciseDeletedBetweenTheCheckAndTheReadRefusesTheCreation() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        when(exerciseService.findOwned(any())).thenReturn(Map.of(squat.getId(), squat));

        Throwable thrown = catchThrowable(() -> service.create(request("Haut du corps A")));

        assertDomainError(thrown, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
        verify(workoutRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesADuplicateNameOfTheSameCoachAndWritesNothing() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(workoutRepository.existsByCoachIdAndName(coach.getId(), "haut du corps a")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.create(request("haut du corps a")));

        assertDomainError(thrown, BusinessRuleException.class, WorkoutErrors.WORKOUT_NAME_ALREADY_EXISTS);
        assertThat(((DomainException) thrown).getField()).isEqualTo("name");
        verify(workoutRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesTheUniqueConstraintIntoTheSameError() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        stubExercisesOfCoach();
        when(workoutRepository.saveAndFlush(any())).thenThrow(duplicateNameViolation());

        Throwable thrown = catchThrowable(() -> service.create(request("Haut du corps A")));

        assertDomainError(thrown, BusinessRuleException.class, WorkoutErrors.WORKOUT_NAME_ALREADY_EXISTS);
        assertThat(((DomainException) thrown).getField()).isEqualTo("name");
    }

    @Test
    void otherConstraintViolationsAreNotHiddenAsADuplicate() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        stubExercisesOfCoach();
        DataIntegrityViolationException other = new DataIntegrityViolationException("autre",
                new SQLIntegrityConstraintViolationException("Check constraint 'ck_planned_set_reps' is violated."));
        when(workoutRepository.saveAndFlush(any())).thenThrow(other);

        Throwable thrown = catchThrowable(() -> service.create(request("Haut du corps A")));

        assertThat(thrown).isSameAs(other);
    }

    // ---------------------------------------------------------------- modification (PUT)

    @Test
    void updateReplacesTheWholeWorkoutAfterFlushingTheRemovalOfTheOldChildren() {
        when(currentUser.id()).thenReturn(coach.getId());
        Workout workout = savedWorkout("Haut du corps A");
        stubDetail(workout);
        stubExercisesOfCoach();
        when(workoutRepository.saveAndFlush(workout)).thenReturn(workout);
        WorkoutRequest reordered = new WorkoutRequest("Haut du corps A", "Nouvelles consignes", List.of(
                new WorkoutExerciseRequest(bench.getId(), null, List.of(set(12, "40", 60))),
                new WorkoutExerciseRequest(squat.getId(), "Lent", List.of(set(5, "100", 180), set(5, "100", 180)))));

        WorkoutResponse updated = service.update(workout.getId(), reordered);

        assertThat(updated.description()).isEqualTo("Nouvelles consignes");
        assertThat(updated.exercises()).extracting(e -> e.exercise().name())
                .containsExactly("Développé couché", "Squat");
        assertThat(updated.exercises().get(1).sets()).hasSize(2);
        assertThat(workout.getRevision()).isEqualTo(1);
        InOrder order = inOrder(accessGuard, workoutRepository);
        order.verify(accessGuard).assertWorkoutOwner(workout.getId());
        order.verify(accessGuard).assertExercisesOwner(List.of(bench.getId(), squat.getId()));
        order.verify(workoutRepository).existsByCoachIdAndNameAndIdNot(coach.getId(), "Haut du corps A",
                workout.getId());
        order.verify(workoutRepository).flush();
        order.verify(workoutRepository).saveAndFlush(workout);
    }

    @Test
    void updateToANameOfAnotherWorkoutOfTheCoachIsRefusedAndChangesNothing() {
        when(currentUser.id()).thenReturn(coach.getId());
        Workout workout = savedWorkout("Haut du corps A");
        stubDetail(workout);
        when(workoutRepository.existsByCoachIdAndNameAndIdNot(coach.getId(), "Bas du corps", workout.getId()))
                .thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.update(workout.getId(), request("Bas du corps")));

        assertDomainError(thrown, BusinessRuleException.class, WorkoutErrors.WORKOUT_NAME_ALREADY_EXISTS);
        assertThat(workout.getName()).isEqualTo("Haut du corps A");
        assertThat(workout.getExercises()).hasSize(2);
        verify(workoutRepository, never()).flush();
        verify(workoutRepository, never()).saveAndFlush(any());
    }

    @Test
    void theWorkoutOfAnotherCoachCannotBeModifiedEvenWithValidExercises() {
        UUID foreign = UUID.randomUUID();
        doThrow(WorkoutErrors.workoutNotFound()).when(accessGuard).assertWorkoutOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.update(foreign, request("Haut du corps A")));

        assertDomainError(thrown, ResourceNotFoundException.class, WorkoutErrors.WORKOUT_NOT_FOUND);
        verify(accessGuard, never()).assertExercisesOwner(any());
        verifyNoInteractions(workoutRepository, exerciseService);
    }

    @Test
    void updateWithAnExerciseOfAnotherCoachIsRefusedAndChangesNothing() {
        Workout workout = savedWorkout("Haut du corps A");
        doThrow(ExerciseErrors.exerciseNotFound()).when(accessGuard).assertExercisesOwner(any());

        Throwable thrown = catchThrowable(() -> service.update(workout.getId(), request("Haut du corps A")));

        assertDomainError(thrown, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
        assertThat(workout.getExercises()).hasSize(2);
        verifyNoInteractions(workoutRepository);
    }

    @Test
    void updateTranslatesTheUniqueConstraintRaisedByTheIntermediateFlush() {
        when(currentUser.id()).thenReturn(coach.getId());
        Workout workout = savedWorkout("Haut du corps A");
        stubDetail(workout);
        stubExercisesOfCoach();
        doThrow(duplicateNameViolation()).when(workoutRepository).flush();

        Throwable thrown = catchThrowable(() -> service.update(workout.getId(), request("Bas du corps")));

        assertDomainError(thrown, BusinessRuleException.class, WorkoutErrors.WORKOUT_NAME_ALREADY_EXISTS);
        verify(workoutRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- duplication

    @Test
    void duplicatesDeeplyForTheSameCoachUnderTheFirstFreeCopyName() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        Workout source = savedWorkout("Haut du corps A");
        stubDetail(source);
        when(workoutRepository.existsByCoachIdAndName(coach.getId(), "Haut du corps A (copie)")).thenReturn(true);
        when(workoutRepository.existsByCoachIdAndName(coach.getId(), "Haut du corps A (copie 2)")).thenReturn(false);
        when(workoutRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        WorkoutResponse copy = service.duplicate(source.getId());

        assertThat(copy.name()).isEqualTo("Haut du corps A (copie 2)");
        assertThat(copy.id()).isNotEqualTo(source.getId());
        assertThat(copy.description()).isEqualTo("Consignes");
        ArgumentCaptor<Workout> saved = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).saveAndFlush(saved.capture());
        Workout written = saved.getValue();
        assertThat(written).isNotSameAs(source);
        assertThat(written.getCoach()).isSameAs(coach);
        assertThat(written.getExercises()).hasSize(2);
        for (int i = 0; i < 2; i++) {
            WorkoutExercise original = source.getExercises().get(i);
            WorkoutExercise copied = written.getExercises().get(i);
            assertThat(copied).isNotSameAs(original);
            assertThat(copied.getId()).isNull();
            assertThat(copied.getWorkout()).isSameAs(written);
            assertThat(copied.getExercise()).isSameAs(original.getExercise());
            assertThat(copied.getNote()).isEqualTo(original.getNote());
            assertThat(copied.getPosition()).isEqualTo(i);
            assertThat(copied.getSets()).extracting(PlannedSet::getPosition, PlannedSet::getReps,
                            PlannedSet::getLoadKg, PlannedSet::getRestSeconds)
                    .containsExactlyElementsOf(original.getSets().stream()
                            .map(s -> org.assertj.core.groups.Tuple.tuple(s.getPosition(), s.getReps(), s.getLoadKg(),
                                    s.getRestSeconds()))
                            .toList());
            assertThat(copied.getSets()).allSatisfy(s -> {
                assertThat(s.getId()).isNull();
                assertThat(s.getWorkoutExercise()).isSameAs(copied);
            });
        }
        // L'original n'a pas bougé.
        assertThat(source.getName()).isEqualTo("Haut du corps A");
        assertThat(source.getExercises().get(0).getWorkout()).isSameAs(source);
    }

    @Test
    void theWorkoutOfAnotherCoachCannotBeDuplicated() {
        UUID foreign = UUID.randomUUID();
        doThrow(WorkoutErrors.workoutNotFound()).when(accessGuard).assertWorkoutOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.duplicate(foreign));

        assertDomainError(thrown, ResourceNotFoundException.class, WorkoutErrors.WORKOUT_NOT_FOUND);
        verifyNoInteractions(workoutRepository);
    }

    @Test
    void aCopyNameTakenMeanwhileAnswers409WithoutAField() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        Workout source = savedWorkout("Haut du corps A");
        stubDetail(source);
        when(workoutRepository.saveAndFlush(any())).thenThrow(duplicateNameViolation());

        Throwable thrown = catchThrowable(() -> service.duplicate(source.getId()));

        assertDomainError(thrown, BusinessRuleException.class, WorkoutErrors.WORKOUT_NAME_ALREADY_EXISTS);
        assertThat(((DomainException) thrown).getField()).isNull();
    }

    @Test
    void copyNamesAreNumberedAndTruncatedToFitOneHundredCharacters() {
        assertThat(WorkoutService.copyCandidate("A", 1)).isEqualTo("A (copie)");
        assertThat(WorkoutService.copyCandidate("A", 2)).isEqualTo("A (copie 2)");
        assertThat(WorkoutService.copyCandidate("A (copie)", 1)).isEqualTo("A (copie) (copie)");

        String longName = "x".repeat(100);
        assertThat(WorkoutService.copyCandidate(longName, 1)).hasSize(100).endsWith("x (copie)");
        assertThat(WorkoutService.copyCandidate(longName, 12)).hasSize(100).endsWith("x (copie 12)");
        // Une coupe qui tomberait sur un espace ne laisse pas de double espace.
        String spaced = "y".repeat(91) + " " + "z".repeat(8);
        assertThat(WorkoutService.copyCandidate(spaced, 1)).isEqualTo("y".repeat(91) + " (copie)");
        // Une coupe ne sépare jamais une paire de substitution (emoji).
        String emoji = "w".repeat(91) + "💪" + "w";
        assertThat(WorkoutService.copyCandidate(emoji, 1)).isEqualTo("w".repeat(91) + " (copie)");
    }

    @Test
    void copyNameTriesTheSuffixesInOrder() {
        when(workoutRepository.existsByCoachIdAndName(eq(coach.getId()), anyString())).thenReturn(true, true, false);

        assertThat(service.copyName(coach.getId(), "Jambes")).isEqualTo("Jambes (copie 3)");
    }

    // ---------------------------------------------------------------- suppression

    @Test
    void deletesAWorkoutOfTheCoachInOneQuery() {
        when(currentUser.id()).thenReturn(coach.getId());
        UUID id = UUID.randomUUID();
        when(workoutRepository.deleteByIdAndCoachId(id, coach.getId())).thenReturn(1);

        service.delete(id);

        verify(accessGuard).assertWorkoutOwner(id);
        verify(workoutRepository).deleteByIdAndCoachId(id, coach.getId());
    }

    @Test
    void aWorkoutDeletedMeanwhileAnswers404() {
        when(currentUser.id()).thenReturn(coach.getId());
        UUID id = UUID.randomUUID();
        when(workoutRepository.deleteByIdAndCoachId(id, coach.getId())).thenReturn(0);

        Throwable thrown = catchThrowable(() -> service.delete(id));

        assertDomainError(thrown, ResourceNotFoundException.class, WorkoutErrors.WORKOUT_NOT_FOUND);
    }

    @Test
    void theWorkoutOfAnotherCoachCannotBeDeleted() {
        UUID foreign = UUID.randomUUID();
        doThrow(WorkoutErrors.workoutNotFound()).when(accessGuard).assertWorkoutOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.delete(foreign));

        assertDomainError(thrown, ResourceNotFoundException.class, WorkoutErrors.WORKOUT_NOT_FOUND);
        verifyNoInteractions(workoutRepository);
    }
}
