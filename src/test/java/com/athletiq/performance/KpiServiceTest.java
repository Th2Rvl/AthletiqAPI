package com.athletiq.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.AthleteLinkService;
import com.athletiq.athlete.dto.AthleteRef;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.group.GroupErrors;
import com.athletiq.performance.dto.AthleteKpi;
import com.athletiq.performance.dto.CoachKpisResponse;
import com.athletiq.performance.dto.KpiTotals;
import com.athletiq.performance.dto.WatchReason;
import com.athletiq.performance.dto.WatchlistEntry;
import com.athletiq.planning.PlanningClock;
import com.athletiq.planning.PlanningProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tableau de bord des KPI, sans base. L'horloge est fixée au 4 novembre 2026 à 23 h 30 UTC, soit le 5 novembre
 * à 0 h 30 à Paris : « aujourd'hui » est le 5, exclu de la période.
 */
@ExtendWith(MockitoExtension.class)
class KpiServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T23:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final KpiProperties DEFAULTS = new KpiProperties(7, 1, 70, 3);

    @Mock
    private SessionPerformanceRepository performanceRepository;
    @Mock
    private AthleteLinkService athleteLinkService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private final UUID coachId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final AthleteRef nathan = new AthleteRef(UUID.randomUUID(), "Nathan", "Chevalier");
    private final AthleteRef hugo = new AthleteRef(UUID.randomUUID(), "Hugo", "Fontaine");
    private final AthleteRef lucas = new AthleteRef(UUID.randomUUID(), "Lucas", "Girard");
    private final AthleteRef emma = new AthleteRef(UUID.randomUUID(), "Emma", "Lefèvre");

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.id()).thenReturn(coachId);
    }

    private KpiService service(KpiProperties properties) {
        return new KpiService(performanceRepository, athleteLinkService, accessGuard, currentUser,
                new PlanningClock(Clock.fixed(NOW, ZoneOffset.UTC), new PlanningProperties(PlanningProperties.DEFAULT_ZONE)),
                properties);
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    private List<UUID> ids(AthleteRef... athletes) {
        return java.util.Arrays.stream(athletes).map(AthleteRef::athleteId).toList();
    }

    /** Nathan sans séance ; Hugo 1 réalisée sur 3 ; Lucas 2 sur 4 dont 2 non faites récentes ; Emma 3 sur 3. */
    private void stubFourAthletes(UUID group, LocalDate from, LocalDate recentFrom) {
        when(athleteLinkService.listFollowed(coachId, group)).thenReturn(List.of(nathan, hugo, lucas, emma));
        when(performanceRepository.aggregateKpis(coachId, ids(nathan, hugo, lucas, emma), from, TODAY.minusDays(1),
                recentFrom)).thenReturn(List.of(
                new AthleteKpiRow(emma.athleteId(), 3, 0, 0, 15L, 2),
                new AthleteKpiRow(lucas.athleteId(), 2, 2, 2, 14L, 2),
                new AthleteKpiRow(hugo.athleteId(), 1, 2, 0, 9L, 1)));
    }

    @Test
    void theKpisGiveEachFollowedAthleteAndPooledTotalsOverTheLastFourWeeksTodayExcluded() {
        stubFourAthletes(null, TODAY.minusDays(28), TODAY.minusDays(7));

        CoachKpisResponse kpis = service(DEFAULTS).kpis(4, null);

        verify(accessGuard).assertCoach();
        verify(accessGuard, never()).assertGroupOwner(any());
        assertThat(kpis.today()).isEqualTo(TODAY);
        assertThat(kpis.from()).isEqualTo(TODAY.minusDays(28));
        assertThat(kpis.to()).isEqualTo(TODAY.minusDays(1));
        assertThat(kpis.weeks()).isEqualTo(4);
        assertThat(kpis.groupId()).isNull();
        // Cumuls : 6 réalisées sur 10 séances passées = 60 % ; RPE (9 + 14 + 15) / 5 = 7,6.
        assertThat(kpis.totals()).isEqualTo(new KpiTotals(4, 6, 4, 60, new BigDecimal("7.6")));
        assertThat(kpis.athletes()).containsExactly(
                new AthleteKpi(nathan.athleteId(), "Nathan", "Chevalier", 0, 0, null, null, 0, false),
                new AthleteKpi(hugo.athleteId(), "Hugo", "Fontaine", 1, 2, 33, new BigDecimal("9.0"), 0, true),
                new AthleteKpi(lucas.athleteId(), "Lucas", "Girard", 2, 2, 50, new BigDecimal("7.0"), 2, true),
                new AthleteKpi(emma.athleteId(), "Emma", "Lefèvre", 3, 0, 100, new BigDecimal("7.5"), 0, false));
        // Non faites décroissant, puis non faites récentes décroissant.
        assertThat(kpis.watchlist()).containsExactly(
                new WatchlistEntry(lucas.athleteId(), "Lucas", "Girard", 2, 2, 50,
                        List.of(WatchReason.MISSED_RECENTLY, WatchReason.LOW_COMPLETION)),
                new WatchlistEntry(hugo.athleteId(), "Hugo", "Fontaine", 2, 0, 33,
                        List.of(WatchReason.LOW_COMPLETION)));
    }

    @Test
    void twelveWeeksStartEightyFourDaysAgoAndTheRecentWindowStaysSevenDays() {
        stubFourAthletes(null, TODAY.minusDays(84), TODAY.minusDays(7));

        CoachKpisResponse kpis = service(DEFAULTS).kpis(12, null);

        assertThat(kpis.from()).isEqualTo(TODAY.minusDays(84));
        assertThat(kpis.weeks()).isEqualTo(12);
    }

    @Test
    void aGroupIsCheckedThenFiltersTheAthletes() {
        when(athleteLinkService.listFollowed(coachId, groupId)).thenReturn(List.of(lucas));
        when(performanceRepository.aggregateKpis(coachId, List.of(lucas.athleteId()), TODAY.minusDays(28),
                TODAY.minusDays(1), TODAY.minusDays(7))).thenReturn(List.of());

        CoachKpisResponse kpis = service(DEFAULTS).kpis(4, groupId);

        InOrder order = inOrder(accessGuard, athleteLinkService);
        order.verify(accessGuard).assertCoach();
        order.verify(accessGuard).assertGroupOwner(groupId);
        order.verify(athleteLinkService).listFollowed(coachId, groupId);
        assertThat(kpis.groupId()).isEqualTo(groupId);
        // Athlète sans séance passée : taux « - » et RPE moyen absents, jamais 0.
        assertThat(kpis.athletes()).singleElement().satisfies(athlete -> {
            assertThat(athlete.completionRate()).isNull();
            assertThat(athlete.averageRpe()).isNull();
            assertThat(athlete.watch()).isFalse();
        });
        assertThat(kpis.totals()).isEqualTo(new KpiTotals(1, 0, 0, null, null));
    }

    @Test
    void withoutFollowedAthleteNothingIsAggregated() {
        when(athleteLinkService.listFollowed(coachId, null)).thenReturn(List.of());

        CoachKpisResponse kpis = service(DEFAULTS).kpis(4, null);

        assertThat(kpis.athletes()).isEmpty();
        assertThat(kpis.watchlist()).isEmpty();
        assertThat(kpis.totals()).isEqualTo(new KpiTotals(0, 0, 0, null, null));
        verifyNoInteractions(performanceRepository);
    }

    @Test
    void anAthleteWithDoneSessionsWithoutRpeHasNoAverageRpe() {
        when(athleteLinkService.listFollowed(coachId, null)).thenReturn(List.of(emma));
        when(performanceRepository.aggregateKpis(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new AthleteKpiRow(emma.athleteId(), 2, 0, 0, null, 0)));

        CoachKpisResponse kpis = service(DEFAULTS).kpis(4, null);

        assertThat(kpis.athletes().getFirst().averageRpe()).isNull();
        assertThat(kpis.athletes().getFirst().completionRate()).isEqualTo(100);
        assertThat(kpis.totals().averageRpe()).isNull();
    }

    @Test
    void theThresholdsComeFromTheProperties() {
        // 3 jours, 2 non faites récentes, 50 %, 5 séances passées.
        stubFourAthletes(null, TODAY.minusDays(28), TODAY.minusDays(3));

        CoachKpisResponse kpis = service(new KpiProperties(3, 2, 50, 5)).kpis(4, null);

        // Lucas : 2 non faites récentes → signalé ; 4 séances passées (< 5) : son taux n'est pas jugé.
        assertThat(kpis.watchlist()).singleElement().satisfies(entry -> {
            assertThat(entry.athleteId()).isEqualTo(lucas.athleteId());
            assertThat(entry.reasons()).containsExactly(WatchReason.MISSED_RECENTLY);
        });
    }

    @Test
    void aPeriodOtherThanFourOrTwelveWeeksAnswers400BeforeAnyLookup() {
        for (int weeks : List.of(0, 1, 8, 26, 52)) {
            Throwable thrown = catchThrowable(() -> service(DEFAULTS).kpis(weeks, null));
            assertDomainError(thrown, InvalidParameterException.class, ErrorCodes.INVALID_PARAMETER);
        }
        verifyNoInteractions(accessGuard, athleteLinkService, performanceRepository);
    }

    @Test
    void theGroupOfAnotherCoachAnswers404AndANonCoachIsRefused() {
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(groupId);

        Throwable thrown = catchThrowable(() -> service(DEFAULTS).kpis(4, groupId));

        assertDomainError(thrown, ResourceNotFoundException.class, GroupErrors.GROUP_NOT_FOUND);
        doThrow(new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé")).when(accessGuard).assertCoach();
        Throwable refused = catchThrowable(() -> service(DEFAULTS).kpis(4, null));
        assertDomainError(refused, ForbiddenActionException.class, ErrorCodes.ACCESS_DENIED);
        verifyNoInteractions(athleteLinkService, performanceRepository);
    }

    @Test
    void inconsistentThresholdsPreventTheStartup() {
        for (Runnable invalid : List.<Runnable>of(
                () -> new KpiProperties(0, 1, 70, 3),
                () -> new KpiProperties(29, 1, 70, 3),
                () -> new KpiProperties(7, 0, 70, 3),
                () -> new KpiProperties(7, 1, 101, 3),
                () -> new KpiProperties(7, 1, 70, 0))) {
            assertThat(catchThrowable(invalid::run)).isInstanceOf(IllegalStateException.class);
        }
    }
}
