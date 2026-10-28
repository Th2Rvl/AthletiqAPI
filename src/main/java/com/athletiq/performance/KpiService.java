package com.athletiq.performance;

import com.athletiq.athlete.AthleteLinkService;
import com.athletiq.athlete.dto.AthleteRef;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.performance.dto.AthleteKpi;
import com.athletiq.performance.dto.CoachKpisResponse;
import com.athletiq.performance.dto.KpiTotals;
import com.athletiq.performance.dto.WatchReason;
import com.athletiq.performance.dto.WatchlistEntry;
import com.athletiq.planning.PlanningClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tableau de bord des KPI du coach connecté (US32), calculé à la lecture (RM7) sur ses seules séances planifiées
 * (RM1) et ses athlètes suivis par un lien actif (RM11). La période couvre les {@code weeks} dernières semaines,
 * aujourd'hui exclu (Q13) : une séance passée est réalisée si sa réalisation est validée, non faite sinon ; une
 * séance annulée n'existe plus, une séance à venir n'est pas encore passée. Deux requêtes : les athlètes, puis
 * un agrégat groupé par athlète.
 *
 * <p>Comme {@link PerformanceService}, la méthode publique contrôle l'accès puis délègue à la méthode à
 * {@code coachId} explicite, qui ne contrôle rien et filtre chaque requête par ce coach.
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    /** Q13 : 4 semaines glissantes (par défaut) ou 12. */
    static final Set<Integer> WEEKS = Set.of(4, 12);

    private final SessionPerformanceRepository performanceRepository;
    private final AthleteLinkService athleteLinkService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final PlanningClock planningClock;
    private final KpiProperties properties;

    /**
     * Période refusée : 400 {@code INVALID_PARAMETER}, avant tout accès. Groupe facultatif, qui doit
     * appartenir au coach (404 {@code GROUP_NOT_FOUND}).
     */
    @Transactional(readOnly = true)
    public CoachKpisResponse kpis(int weeks, UUID groupId) {
        if (!WEEKS.contains(weeks)) {
            throw new InvalidParameterException("weeks");
        }
        accessGuard.assertCoach();
        if (groupId != null) {
            accessGuard.assertGroupOwner(groupId);
        }
        return kpis(currentUser.id(), weeks, groupId);
    }

    /** Sans contrôle d'accès : l'appelant a vérifié le rôle, la propriété du groupe et la période. */
    @Transactional(readOnly = true)
    CoachKpisResponse kpis(UUID coachId, int weeks, UUID groupId) {
        Objects.requireNonNull(coachId, "coachId");
        LocalDate today = planningClock.today();
        LocalDate from = today.minusWeeks(weeks);
        LocalDate to = today.minusDays(1);
        List<AthleteRef> athletes = athleteLinkService.listFollowed(coachId, groupId);
        Map<UUID, AthleteKpiRow> rows = athletes.isEmpty()
                ? Map.of()
                : performanceRepository.aggregateKpis(coachId, athletes.stream().map(AthleteRef::athleteId).toList(),
                                from, to, today.minusDays(properties.missedDays())).stream()
                        .collect(Collectors.toMap(AthleteKpiRow::athleteId, Function.identity()));

        List<Indicators> indicators = athletes.stream()
                .map(athlete -> indicators(athlete, rows.get(athlete.athleteId())))
                .toList();
        List<AthleteKpi> athleteKpis = indicators.stream().map(Indicators::toAthleteKpi).toList();
        // Tri stable : à égalité, l'ordre des athlètes (nom, prénom, comme en base) est conservé.
        List<WatchlistEntry> watchlist = indicators.stream()
                .filter(Indicators::watch)
                .sorted(Comparator.comparingInt(Indicators::missed).reversed()
                        .thenComparing(Comparator.comparingInt(Indicators::missedRecently).reversed()))
                .map(Indicators::toWatchlistEntry)
                .toList();
        return new CoachKpisResponse(today, from, to, weeks, groupId, totals(athletes.size(), rows.values()),
                athleteKpis, watchlist);
    }

    /** Indicateurs d'un athlète ; sans ligne d'agrégat, il n'a aucune séance passée sur la période. */
    private Indicators indicators(AthleteRef athlete, AthleteKpiRow row) {
        int done = row == null ? 0 : Math.toIntExact(row.doneCount());
        int missed = row == null ? 0 : Math.toIntExact(row.missedCount());
        int missedRecently = row == null ? 0 : Math.toIntExact(row.missedRecently());
        Integer completionRate = PerformanceMath.completionRate(done, missed);
        BigDecimal averageRpe = row == null ? null : PerformanceMath.average(rpeSum(row), row.rpeCount());
        List<WatchReason> reasons = new ArrayList<>();
        if (missedRecently >= properties.missedThreshold()) {
            reasons.add(WatchReason.MISSED_RECENTLY);
        }
        if (done + missed >= properties.minPastSessions() && completionRate != null
                && completionRate < properties.minCompletionRate()) {
            reasons.add(WatchReason.LOW_COMPLETION);
        }
        return new Indicators(athlete, done, missed, missedRecently, completionRate, averageRpe, List.copyOf(reasons));
    }

    /** Cumuls de tous les athlètes listés : taux et RPE moyen recalculés sur les sommes, jamais moyennés. */
    private static KpiTotals totals(int athleteCount, Iterable<AthleteKpiRow> rows) {
        long done = 0;
        long missed = 0;
        long rpeSum = 0;
        long rpeCount = 0;
        for (AthleteKpiRow row : rows) {
            done += row.doneCount();
            missed += row.missedCount();
            rpeSum += rpeSum(row);
            rpeCount += row.rpeCount();
        }
        return new KpiTotals(athleteCount, Math.toIntExact(done), Math.toIntExact(missed),
                PerformanceMath.completionRate(done, missed), PerformanceMath.average(rpeSum, rpeCount));
    }

    private static long rpeSum(AthleteKpiRow row) {
        return row.rpeSum() == null ? 0 : row.rpeSum();
    }

    private record Indicators(AthleteRef athlete, int done, int missed, int missedRecently, Integer completionRate,
                              BigDecimal averageRpe, List<WatchReason> reasons) {

        boolean watch() {
            return !reasons.isEmpty();
        }

        AthleteKpi toAthleteKpi() {
            return new AthleteKpi(athlete.athleteId(), athlete.firstName(), athlete.lastName(), done, missed,
                    completionRate, averageRpe, missedRecently, watch());
        }

        WatchlistEntry toWatchlistEntry() {
            return new WatchlistEntry(athlete.athleteId(), athlete.firstName(), athlete.lastName(), missed,
                    missedRecently, completionRate, reasons);
        }
    }
}
