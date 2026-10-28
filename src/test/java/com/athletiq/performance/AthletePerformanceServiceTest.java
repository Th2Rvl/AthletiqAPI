package com.athletiq.performance;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.InvalidFieldException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.performance.dto.AthletePerformanceResponse;
import com.athletiq.performance.dto.PerformedSetInput;
import com.athletiq.performance.dto.SavePerformanceRequest;
import com.athletiq.planning.PlannedSession;
import com.athletiq.planning.SessionSnapshot;
import com.athletiq.planning.SessionSnapshotSet;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AthletePerformanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Mock
    private SessionPerformanceRepository repository;
    @Mock
    private UserService userService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private AthletePerformanceService service;
    private User athlete;
    private User coach;
    private PlannedSession planned;
    private SessionSnapshotSet set1;
    private SessionSnapshotSet set2;

    @BeforeEach
    void setUp() {
        service = new AthletePerformanceService(repository, userService, accessGuard, currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC));
        athlete = user("lea@mail.fr", Role.ATHLETE);
        coach = user("coach@club.fr", Role.COACH);
        SessionSnapshot snapshot = withId(SessionSnapshot.builder().coach(coach).name("Haut du corps A").build(),
                UUID.randomUUID());
        planned = withId(PlannedSession.builder().coach(coach).athlete(athlete)
                .scheduledDate(LocalDate.of(2026, 10, 28)).snapshot(snapshot).build(), UUID.randomUUID());
        set1 = withId(SessionSnapshotSet.builder().position(0).reps(8).loadKg(new BigDecimal("72.5")).build(), UUID.randomUUID());
        set2 = withId(SessionSnapshotSet.builder().position(1).reps(6).loadKg(new BigDecimal("77.5")).build(), UUID.randomUUID());
        lenient().when(currentUser.id()).thenReturn(athlete.getId());
        lenient().when(userService.getReference(athlete.getId())).thenReturn(athlete);
        lenient().when(repository.findOwnPlannedSession(planned.getId(), athlete.getId())).thenReturn(Optional.of(planned));
        lenient().when(repository.findPlannedSets(planned.getId())).thenReturn(List.of(set1, set2));
        lenient().when(repository.findOwnWithSets(planned.getId(), athlete.getId())).thenReturn(Optional.empty());
        lenient().when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private SavePerformanceRequest request(Integer reps1, Integer reps2, Integer rpe, String comment) {
        return new SavePerformanceRequest(List.of(
                new PerformedSetInput(set1.getId(), reps1, new BigDecimal("72.5")),
                new PerformedSetInput(set2.getId(), reps2, new BigDecimal("77.5"))), rpe, 3, comment);
    }

    @Test
    void nothingIsEnteredYetBeforeTheFirstSave() {
        AthletePerformanceResponse response = service.get(planned.getId());

        assertThat(response.status()).isNull();
        assertThat(response.sets()).isEmpty();
    }

    @Test
    void savingCreatesADraftWithoutValidating() {
        AthletePerformanceResponse response = service.save(planned.getId(), request(8, null, 7, "  Bonne séance  "));

        assertThat(response.status()).isEqualTo(PerformanceStatus.DRAFT);
        assertThat(response.validatedAt()).isNull();
        assertThat(response.comment()).isEqualTo("Bonne séance");
        assertThat(response.rpe()).isEqualTo(7);
        assertThat(response.sets()).hasSize(2);
        assertThat(response.sets().get(1).reps()).isNull();
    }

    @Test
    void aBlankCommentIsStoredAsNothingAndALoadWithoutRepsIsDropped() {
        AthletePerformanceResponse response = service.save(planned.getId(), request(8, null, null, "   "));

        assertThat(response.comment()).isNull();
        assertThat(response.rpe()).isNull();
        assertThat(response.sets().get(1).loadKg()).isNull();
        assertThat(response.sets().get(0).loadKg()).isEqualByComparingTo("72.5");
    }

    @Test
    void validatingDatesTheValidationAndRequiresAtLeastOneSetDone() {
        AthletePerformanceResponse response = service.validate(planned.getId(), request(8, 5, 8, null));

        assertThat(response.status()).isEqualTo(PerformanceStatus.VALIDATED);
        assertThat(response.validatedAt()).isEqualTo(NOW);
        assertThat(response.correctedAt()).isNull();
    }

    @Test
    void refusesToValidateASessionWithNothingDoneAndSavesNothing() {
        assertThatThrownBy(() -> service.validate(planned.getId(), request(null, null, 7, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("au moins une série");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void savingAValidatedSessionIsACorrectionThatKeepsItValidated() {
        SessionPerformance validated = SessionPerformance.builder().plannedSession(planned).athlete(athlete)
                .status(PerformanceStatus.VALIDATED).validatedAt(NOW.minusSeconds(3600)).rpe(6).build();
        validated.addSet(PerformedSet.builder().snapshotSet(set1).reps(8).loadKg(new BigDecimal("72.5")).build());
        validated.addSet(PerformedSet.builder().snapshotSet(set2).reps(6).loadKg(new BigDecimal("77.5")).build());
        when(repository.findOwnWithSets(planned.getId(), athlete.getId())).thenReturn(Optional.of(validated));

        AthletePerformanceResponse response = service.save(planned.getId(), request(10, 5, 9, null));

        assertThat(response.status()).isEqualTo(PerformanceStatus.VALIDATED);
        assertThat(response.validatedAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(response.correctedAt()).isEqualTo(NOW);
        assertThat(response.rpe()).isEqualTo(9);
        assertThat(response.sets()).extracting(set -> set.reps()).containsExactly(10, 5);
    }

    @Test
    void updatesExistingRowsInPlaceAndRemovesTheOnesNoLongerSent() {
        SessionPerformance draft = SessionPerformance.builder().plannedSession(planned).athlete(athlete)
                .status(PerformanceStatus.DRAFT).build();
        PerformedSet row1 = PerformedSet.builder().snapshotSet(set1).reps(8).loadKg(new BigDecimal("72.5")).build();
        draft.addSet(row1);
        draft.addSet(PerformedSet.builder().snapshotSet(set2).reps(6).loadKg(new BigDecimal("77.5")).build());
        when(repository.findOwnWithSets(planned.getId(), athlete.getId())).thenReturn(Optional.of(draft));

        AthletePerformanceResponse response = service.save(planned.getId(), new SavePerformanceRequest(
                List.of(new PerformedSetInput(set1.getId(), 9, new BigDecimal("75"))), null, null, null));

        assertThat(response.sets()).singleElement().satisfies(set -> {
            assertThat(set.reps()).isEqualTo(9);
            assertThat(set.loadKg()).isEqualByComparingTo("75");
        });
        assertThat(draft.getSets()).containsExactly(row1);
    }

    @Test
    void refusesASetThatIsNotInThisSessionOrSentTwice() {
        SavePerformanceRequest foreign = new SavePerformanceRequest(
                List.of(new PerformedSetInput(UUID.randomUUID(), 8, null)), null, null, null);
        SavePerformanceRequest twice = new SavePerformanceRequest(List.of(
                new PerformedSetInput(set1.getId(), 8, null), new PerformedSetInput(set1.getId(), 7, null)),
                null, null, null);

        assertThatThrownBy(() -> service.save(planned.getId(), foreign)).isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("ne fait pas partie");
        assertThatThrownBy(() -> service.save(planned.getId(), twice)).isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("deux fois");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aSessionOfAnotherAthleteIsNotFound() {
        UUID other = UUID.randomUUID();
        when(repository.findOwnPlannedSession(other, athlete.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.save(other, request(8, 6, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.validate(other, request(8, 6, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void anAthleteWithoutCoachCannotEnterAnything() {
        doThrow(new ForbiddenActionException("ATHLETE_WITHOUT_COACH", "Rejoignez un coach"))
                .when(accessGuard).assertAthleteWithCoach();

        assertThatThrownBy(() -> service.save(planned.getId(), request(8, 6, null, null)))
                .isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void twoSimultaneousFirstSavesEndInAConflict() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_session_performance"));

        assertThatThrownBy(() -> service.save(planned.getId(), request(8, 6, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("réessayez");
    }
}
