package com.athletiq.performance;

import static com.athletiq.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
import com.athletiq.performance.dto.AthleteKpisResponse;
import com.athletiq.performance.dto.PersonalRecord;
import com.athletiq.performance.dto.ProgressionResponse;
import com.athletiq.performance.dto.SessionHistoryFilter;
import com.athletiq.planning.PlanningClock;
import com.athletiq.planning.PlanningProperties;
import com.athletiq.planning.SessionStatus;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AthleteTrackingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Mock
    private AthleteTrackingRepository repository;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private AthleteTrackingService service;
    private User athlete;

    @BeforeEach
    void setUp() {
        PlanningClock clock = new PlanningClock(Clock.fixed(NOW, ZoneOffset.UTC), new PlanningProperties(null));
        service = new AthleteTrackingService(repository, new PerformanceMapperImpl(), accessGuard, currentUser, clock,
                new KpiProperties(7, 1, 70, 3));
        athlete = user("lea@mail.fr", Role.ATHLETE);
        lenient().when(currentUser.id()).thenReturn(athlete.getId());
    }

    @Test
    void historyDefaultsToTheLastFourWeeksAndMapsStatuses() {
        UUID doneId = UUID.randomUUID();
        UUID missedId = UUID.randomUUID();
        when(repository.findHistory(eq(athlete.getId()), eq(TODAY.minusDays(27)), eq(TODAY), eq(TODAY),
                eq(true), eq(true), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        new SessionHistoryRow(doneId, TODAY.minusDays(1), "Force A", UUID.randomUUID(), 8, 3, true,
                                NOW, null),
                        new SessionHistoryRow(missedId, TODAY.minusDays(3), "Force A", null, null, null, false,
                                null, null))));

        var page = service.sessionHistory(new SessionHistoryFilter(null, null, null, false), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(item -> item.status())
                .containsExactly(SessionStatus.DONE, SessionStatus.MISSED);
        verify(accessGuard).assertAthleteWithCoach();
    }

    @Test
    void historyRefusesAnUpcomingStatusAnInvertedOrTooLongPeriodBeforeAnyRead() {
        assertThatThrownBy(() -> service.sessionHistory(
                new SessionHistoryFilter(null, null, SessionStatus.UPCOMING, false), PageRequest.of(0, 20)))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.sessionHistory(
                new SessionHistoryFilter(TODAY, TODAY.minusDays(1), null, false), PageRequest.of(0, 20)))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.sessionHistory(
                new SessionHistoryFilter(TODAY.minusDays(400), TODAY, null, false), PageRequest.of(0, 20)))
                .isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void recordsKeepTheHeaviestLoadAndTheMostRepsForBodyweight() {
        UUID bench = UUID.randomUUID();
        UUID pullUp = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        when(repository.findPerformedSetGroups(athlete.getId())).thenReturn(List.of(
                new PerformedSetGroupRow(bench, "Développé couché", MuscleGroup.PECTORAUX, s1, TODAY.minusDays(20),
                        NOW, new BigDecimal("60.0"), 8),
                new PerformedSetGroupRow(bench, "Développé couché", MuscleGroup.PECTORAUX, s1, TODAY.minusDays(20),
                        NOW, new BigDecimal("65.0"), 6),
                new PerformedSetGroupRow(bench, "Développé couché", MuscleGroup.PECTORAUX, s2, TODAY.minusDays(10),
                        NOW, new BigDecimal("62.5"), 8),
                new PerformedSetGroupRow(pullUp, "Tractions", MuscleGroup.DOS, s2, TODAY.minusDays(10), NOW, null, 10),
                new PerformedSetGroupRow(pullUp, "Tractions", MuscleGroup.DOS, s1, TODAY.minusDays(20), NOW, null, 8)));

        List<PersonalRecord> records = service.personalRecords().records();

        assertThat(records).extracting(PersonalRecord::exerciseName).containsExactly("Tractions", "Développé couché");
        assertThat(records.get(0).bodyweight()).isTrue();
        assertThat(records.get(0).reps()).isEqualTo(10);
        assertThat(records.get(1).loadKg()).isEqualByComparingTo("65.0");
        assertThat(records.get(1).reps()).isEqualTo(6);
        assertThat(records.get(1).plannedSessionId()).isEqualTo(s1);
    }

    @Test
    void progressionNeedsAKnownPeriodAndAnExerciseTheAthleteDid() {
        UUID exercise = UUID.randomUUID();
        assertThatThrownBy(() -> service.progression(exercise, 5)).isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(repository);

        when(repository.findLatestLabels(eq(athlete.getId()), eq(exercise), any(Pageable.class)))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.progression(exercise, 12)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void progressionBuildsOnePointPerDoneSessionAndDetectsBodyweight() {
        UUID exercise = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        when(repository.findLatestLabels(eq(athlete.getId()), eq(exercise), any(Pageable.class)))
                .thenReturn(List.of(new ExerciseLabel("Tractions", MuscleGroup.DOS)));
        when(repository.findProgressionRows(athlete.getId(), exercise, TODAY.minusWeeks(4)))
                .thenReturn(List.of(new ProgressionRow(session, TODAY.minusDays(3), NOW, null, 10, null)));

        ProgressionResponse response = service.progression(exercise, 4);

        assertThat(response.bodyweight()).isTrue();
        assertThat(response.from()).isEqualTo(TODAY.minusWeeks(4));
        assertThat(response.points()).singleElement().satisfies(point -> {
            assertThat(point.maxReps()).isEqualTo(10);
            assertThat(point.volumeKg()).isNull();
        });
    }

    @Test
    void kpisCountThePastWeeksWithoutTodayAndAverageTheRpe() {
        when(repository.aggregateKpi(athlete.getId(), TODAY.minusWeeks(4), TODAY.minusDays(1), TODAY.minusDays(7)))
                .thenReturn(Optional.of(new AthleteKpiRow(athlete.getId(), 3, 2, 2, 21L, 3)));

        AthleteKpisResponse kpis = service.kpis(4);

        assertThat(kpis.to()).isEqualTo(TODAY.minusDays(1));
        assertThat(kpis.doneCount()).isEqualTo(3);
        assertThat(kpis.missedCount()).isEqualTo(2);
        assertThat(kpis.completionRate()).isEqualTo(60);
        assertThat(kpis.averageRpe()).isEqualByComparingTo("7.0");
        assertThat(kpis.recentMissedCount()).isEqualTo(2);
    }

    @Test
    void kpisAreEmptyWithoutPastSessionsAndRefuseAnUnknownPeriod() {
        when(repository.aggregateKpi(any(), any(), any(), any())).thenReturn(Optional.empty());

        AthleteKpisResponse kpis = service.kpis(12);

        assertThat(kpis.completionRate()).isNull();
        assertThat(kpis.averageRpe()).isNull();
        assertThat(kpis.doneCount()).isZero();
        assertThatThrownBy(() -> service.kpis(8)).isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void anAthleteWithoutCoachReadsNothing() {
        doThrow(AthleteErrors.athleteWithoutCoach()).when(accessGuard).assertAthleteWithCoach();

        assertThatThrownBy(() -> service.personalRecords()).isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.performedExercises()).isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.kpis(4)).isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(repository);
    }
}
