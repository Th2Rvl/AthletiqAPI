package com.athletiq.exercise;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.athletiq.exercise.dto.CreateExerciseRequest;
import com.athletiq.exercise.dto.ExerciseResponse;
import com.athletiq.exercise.dto.UpdateExerciseRequest;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private UserService userService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private ExerciseUsage exerciseUsage;

    private ExerciseService service;
    private User coach;

    @BeforeEach
    void setUp() {
        service = new ExerciseService(exerciseRepository, new ExerciseMapperImpl(), userService, accessGuard,
                currentUser, exerciseUsage);
        coach = user("coach@club.fr", Role.COACH);
    }

    private Exercise exercise(String name) {
        return withId(Exercise.builder()
                .coach(coach)
                .name(name)
                .muscleGroup(MuscleGroup.PECTORAUX)
                .equipment("BARRE")
                .videoUrl("https://example.com/video")
                .description("Pousser la barre")
                .build(), UUID.randomUUID());
    }

    private static CreateExerciseRequest creation(String name) {
        return new CreateExerciseRequest(name, "Pousser la barre", MuscleGroup.PECTORAUX, "BARRE",
                "https://example.com/video");
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    // ---------------------------------------------------------------- liste et recherche

    @Test
    void listsTheExercisesOfTheCoachWithTheFiltersAndTheirPagination() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.search(eq(coach.getId()), eq("%squ%"), eq(MuscleGroup.QUADRICEPS), eq("BARRE"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(squat)));

        List<ExerciseResponse> content = service.list(" squ ", MuscleGroup.QUADRICEPS, " BARRE ",
                PageRequest.of(0, 20)).getContent();

        assertThat(content).extracting(ExerciseResponse::name).containsExactly("Squat");
        assertThat(content.get(0).muscleGroup()).isEqualTo(MuscleGroup.PECTORAUX);
        verify(accessGuard).assertCoach();
    }

    @Test
    void aBlankSearchAndAnEmptyEquipmentMeanNoFilter() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(exerciseRepository.search(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list("   ", null, "  ", PageRequest.of(0, 20));

        verify(exerciseRepository).search(eq(coach.getId()), eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void escapesTheWildcardsOfTheSearch() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(exerciseRepository.search(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list("100%_!", null, null, PageRequest.of(0, 20));

        verify(exerciseRepository).search(eq(coach.getId()), eq("%100!%!_!!%"), eq(null), eq(null),
                any(Pageable.class));
    }

    @Test
    void aNonCoachIsRefusedBeforeAnyQuery() {
        doThrow(new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé"))
                .when(accessGuard).assertCoach();

        Throwable thrown = catchThrowable(() -> service.list(null, null, null, PageRequest.of(0, 20)));

        assertDomainError(thrown, ForbiddenActionException.class, ErrorCodes.ACCESS_DENIED);
        verifyNoInteractions(exerciseRepository);
    }

    // ---------------------------------------------------------------- détail

    @Test
    void readsAnExerciseOfTheCoach() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).thenReturn(Optional.of(squat));

        ExerciseResponse read = service.get(squat.getId());

        assertThat(read.name()).isEqualTo("Squat");
        verify(accessGuard).assertExerciseOwner(squat.getId());
    }

    @Test
    void theExerciseOfAnotherCoachAnswers404AndIsNotRead() {
        UUID foreign = UUID.randomUUID();
        doThrow(ExerciseErrors.exerciseNotFound()).when(accessGuard).assertExerciseOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.get(foreign));

        assertDomainError(thrown, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void theRepositoryStillFiltersByCoachIfTheGuardLetsAnUnknownIdThrough() {
        when(currentUser.id()).thenReturn(coach.getId());
        UUID unknown = UUID.randomUUID();
        when(exerciseRepository.findByIdAndCoachId(unknown, coach.getId())).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.get(unknown));

        assertDomainError(thrown, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
    }

    // ---------------------------------------------------------------- création

    @Test
    void createsAnExerciseOwnedByTheConnectedCoach() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        when(exerciseRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        ExerciseResponse created = service.create(creation("Développé couché"));

        assertThat(created.name()).isEqualTo("Développé couché");
        assertThat(created.muscleGroup()).isEqualTo(MuscleGroup.PECTORAUX);
        verify(exerciseRepository).existsByCoachIdAndName(coach.getId(), "Développé couché");
        verify(accessGuard).assertCoach();
    }

    @Test
    void refusesADuplicateNameOfTheSameCoach() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(exerciseRepository.existsByCoachIdAndName(coach.getId(), "Squat")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.create(creation("Squat")));

        assertDomainError(thrown, BusinessRuleException.class, ExerciseErrors.EXERCISE_NAME_ALREADY_EXISTS);
        assertThat(((DomainException) thrown).getField()).isEqualTo("name");
        verify(exerciseRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesTheUniqueConstraintIntoTheSameError() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        when(exerciseRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("doublon",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'exercise.uk_exercise_coach_name'")));

        Throwable thrown = catchThrowable(() -> service.create(creation("squat")));

        assertDomainError(thrown, BusinessRuleException.class, ExerciseErrors.EXERCISE_NAME_ALREADY_EXISTS);
    }

    @Test
    void otherConstraintViolationsAreNotHiddenAsADuplicate() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        DataIntegrityViolationException other = new DataIntegrityViolationException("autre",
                new SQLIntegrityConstraintViolationException("Column 'name' cannot be null"));
        when(exerciseRepository.saveAndFlush(any())).thenThrow(other);

        Throwable thrown = catchThrowable(() -> service.create(creation("Squat")));

        assertThat(thrown).isSameAs(other);
    }

    // ---------------------------------------------------------------- modification (PUT)

    @Test
    void updateReplacesTheWholeExerciseIncludingClearingOptionalFields() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).thenReturn(Optional.of(squat));
        when(exerciseRepository.saveAndFlush(squat)).thenReturn(squat);

        ExerciseResponse updated = service.update(squat.getId(), new UpdateExerciseRequest("Squat arrière", null,
                MuscleGroup.FESSIERS, null, null));

        assertThat(updated.name()).isEqualTo("Squat arrière");
        assertThat(updated.description()).isNull();
        assertThat(updated.equipment()).isNull();
        assertThat(updated.videoUrl()).isNull();
        assertThat(updated.muscleGroup()).isEqualTo(MuscleGroup.FESSIERS);
        verify(accessGuard).assertExerciseOwner(squat.getId());
    }

    @Test
    void updateKeepingItsOwnNameIsAccepted() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).thenReturn(Optional.of(squat));
        when(exerciseRepository.existsByCoachIdAndNameAndIdNot(coach.getId(), "SQUAT", squat.getId())).thenReturn(false);
        when(exerciseRepository.saveAndFlush(squat)).thenReturn(squat);

        ExerciseResponse updated = service.update(squat.getId(), new UpdateExerciseRequest("SQUAT",
                "Pousser la barre", MuscleGroup.QUADRICEPS, "BARRE", null));

        assertThat(updated.name()).isEqualTo("SQUAT");
    }

    @Test
    void updateToANameOfAnotherExerciseOfTheCoachIsRefused() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).thenReturn(Optional.of(squat));
        when(exerciseRepository.existsByCoachIdAndNameAndIdNot(coach.getId(), "Développé", squat.getId())).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.update(squat.getId(), new UpdateExerciseRequest("Développé",
                null, MuscleGroup.PECTORAUX, null, null)));

        assertDomainError(thrown, BusinessRuleException.class, ExerciseErrors.EXERCISE_NAME_ALREADY_EXISTS);
        assertThat(squat.getName()).isEqualTo("Squat");
        verify(exerciseRepository, never()).saveAndFlush(any());
    }

    @Test
    void theExerciseOfAnotherCoachCannotBeModified() {
        UUID foreign = UUID.randomUUID();
        doThrow(ExerciseErrors.exerciseNotFound()).when(accessGuard).assertExerciseOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.update(foreign, new UpdateExerciseRequest("Squat", null,
                MuscleGroup.QUADRICEPS, null, null)));

        assertDomainError(thrown, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
        verifyNoInteractions(exerciseRepository);
    }

    // ---------------------------------------------------------------- suppression

    @Test
    void deletesAnExerciseOfTheCoach() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).thenReturn(Optional.of(squat));

        service.delete(squat.getId());

        verify(exerciseRepository).delete(squat);
        verify(exerciseRepository).flush();
        verify(accessGuard).assertExerciseOwner(squat.getId());
    }

    @Test
    void anExerciseUsedInWorkoutsCannotBeDeletedAndTheCountIsGiven() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).thenReturn(Optional.of(squat));
        when(exerciseUsage.countWorkoutsUsing(squat.getId())).thenReturn(3L);

        Throwable thrown = catchThrowable(() -> service.delete(squat.getId()));

        assertDomainError(thrown, BusinessRuleException.class, ExerciseErrors.EXERCISE_IN_USE);
        DomainException error = (DomainException) thrown;
        assertThat(error.getProperties()).containsEntry(ExerciseErrors.WORKOUT_COUNT, 3L);
        assertThat(error.getDetail()).isEqualTo("Cet exercice est utilisé dans 3 séances");
        assertThat(error.getField()).isNull();
        verify(exerciseRepository, never()).delete(any());
    }

    @Test
    void theRestrictForeignKeyIsTranslatedIntoExerciseInUseWhenAWorkoutAppearsMeanwhile() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        when(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).thenReturn(Optional.of(squat));
        when(exerciseUsage.countWorkoutsUsing(squat.getId())).thenReturn(0L, 1L);
        doThrow(new DataIntegrityViolationException("fk", new SQLIntegrityConstraintViolationException(
                "Cannot delete or update a parent row: a foreign key constraint fails "
                        + "(CONSTRAINT `fk_workout_exercise_exercise` FOREIGN KEY ...)")))
                .when(exerciseRepository).flush();

        Throwable thrown = catchThrowable(() -> service.delete(squat.getId()));

        assertDomainError(thrown, BusinessRuleException.class, ExerciseErrors.EXERCISE_IN_USE);
        assertThat(((DomainException) thrown).getProperties()).containsEntry(ExerciseErrors.WORKOUT_COUNT, 1L);
        assertThat(((DomainException) thrown).getDetail()).isEqualTo("Cet exercice est utilisé dans 1 séance");
    }

    @Test
    void findOwnedReturnsTheExercisesOfTheCoachByIdInOneQuery() {
        when(currentUser.id()).thenReturn(coach.getId());
        Exercise squat = exercise("Squat");
        Exercise bench = exercise("Développé couché");
        when(exerciseRepository.findAllByCoachIdAndIdIn(coach.getId(), List.of(squat.getId(), bench.getId())))
                .thenReturn(List.of(squat, bench));

        var owned = service.findOwned(List.of(squat.getId(), bench.getId(), squat.getId()));

        assertThat(owned).containsOnlyKeys(squat.getId(), bench.getId());
        assertThat(service.findOwned(List.of())).isEmpty();
    }

    @Test
    void theExerciseOfAnotherCoachCannotBeDeleted() {
        UUID foreign = UUID.randomUUID();
        doThrow(ExerciseErrors.exerciseNotFound()).when(accessGuard).assertExerciseOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.delete(foreign));

        assertDomainError(thrown, ResourceNotFoundException.class, ExerciseErrors.EXERCISE_NOT_FOUND);
        verify(exerciseRepository, never()).delete(any());
    }
}
