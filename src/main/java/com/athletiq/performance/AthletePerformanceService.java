package com.athletiq.performance;

import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.performance.dto.AthletePerformanceResponse;
import com.athletiq.performance.dto.PerformedSetInput;
import com.athletiq.performance.dto.PerformedSetValue;
import com.athletiq.performance.dto.SavePerformanceRequest;
import com.athletiq.planning.PlannedSession;
import com.athletiq.planning.PlanningErrors;
import com.athletiq.planning.SessionSnapshotSet;
import com.athletiq.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saisie du réalisé par l'athlète (#10, US10 à US14). Une séance a au plus une réalisation (RM4) : brouillon
 * tant qu'elle n'est pas validée (invisible du coach, RM3), puis validée. Enregistrer une séance déjà validée
 * est une correction (US14) : elle reste validée et {@code correctedAt} est daté. L'athlète ne touche qu'à ses
 * propres séances (404 sinon) et seulement s'il a un coach (US06). La date de la séance ne limite pas la saisie :
 * une séance peut être validée avant sa date (Q17) ou après (rattrapage).
 */
@Service
@RequiredArgsConstructor
public class AthletePerformanceService {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final SessionPerformanceRepository performanceRepository;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final Clock clock;

    /** US10 à US14 : le réalisé enregistré de cette séance, ou une réponse vide si rien n'est saisi. */
    @Transactional(readOnly = true)
    public AthletePerformanceResponse get(UUID plannedSessionId) {
        accessGuard.assertAthleteWithCoach();
        UUID athleteId = currentUser.id();
        performanceRepository.findOwnPlannedSession(plannedSessionId, athleteId)
                .orElseThrow(PlanningErrors::plannedSessionNotFound);
        return performanceRepository.findOwnWithSets(plannedSessionId, athleteId)
                .map(AthletePerformanceService::toResponse)
                .orElseGet(AthletePerformanceResponse::none);
    }

    /** US10 à US12 : enregistre le réalisé en brouillon, ou corrige une réalisation déjà validée (US14). */
    @Transactional
    public AthletePerformanceResponse save(UUID plannedSessionId, SavePerformanceRequest request) {
        return write(plannedSessionId, request, false);
    }

    /**
     * US13 : enregistre puis valide. Au moins une série doit être faite, sinon 409 {@code PERFORMANCE_EMPTY} et rien
     * n'est enregistré. Valider une séance déjà validée la corrige.
     */
    @Transactional
    public AthletePerformanceResponse validate(UUID plannedSessionId, SavePerformanceRequest request) {
        return write(plannedSessionId, request, true);
    }

    private AthletePerformanceResponse write(UUID plannedSessionId, SavePerformanceRequest request, boolean validate) {
        accessGuard.assertAthleteWithCoach();
        UUID athleteId = currentUser.id();
        PlannedSession plannedSession = performanceRepository.findOwnPlannedSession(plannedSessionId, athleteId)
                .orElseThrow(PlanningErrors::plannedSessionNotFound);
        Map<UUID, SessionSnapshotSet> plannedSets = performanceRepository.findPlannedSets(plannedSessionId).stream()
                .collect(Collectors.toMap(SessionSnapshotSet::getId, Function.identity()));
        assertSets(request.sets(), plannedSets.keySet());
        if (validate && request.sets().stream().noneMatch(set -> set.reps() != null)) {
            throw PerformanceErrors.performanceEmpty();
        }

        // DATETIME(6) en base : tronquer ici évite de renvoyer une date que la relecture ne retrouverait pas.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        SessionPerformance performance = performanceRepository.findOwnWithSets(plannedSessionId, athleteId)
                .orElseGet(() -> SessionPerformance.builder()
                        .plannedSession(plannedSession)
                        .athlete(userService.getReference(athleteId))
                        .status(PerformanceStatus.DRAFT)
                        .build());
        boolean wasValidated = performance.getStatus() == PerformanceStatus.VALIDATED;

        performance.setRpe(request.rpe());
        performance.setFatigue(request.fatigue());
        performance.setComment(normalize(request.comment()));
        applySets(performance, request.sets(), plannedSets);
        if (wasValidated) {
            performance.setCorrectedAt(now);
        } else if (validate) {
            performance.setStatus(PerformanceStatus.VALIDATED);
            performance.setValidatedAt(now);
        }
        try {
            return toResponse(performanceRepository.saveAndFlush(performance));
        } catch (DataIntegrityViolationException ex) {
            // Deux premiers enregistrements simultanés : la contrainte d'unicité de la séance a tranché.
            throw PerformanceErrors.performanceConflict();
        }
    }

    /** Chaque série doit être une série prévue de cette séance, une seule fois. */
    private static void assertSets(List<PerformedSetInput> inputs, Set<UUID> plannedSetIds) {
        Set<UUID> seen = new HashSet<>();
        for (PerformedSetInput input : inputs) {
            if (!plannedSetIds.contains(input.snapshotSetId())) {
                throw PerformanceErrors.performedSetUnknown();
            }
            if (!seen.add(input.snapshotSetId())) {
                throw PerformanceErrors.performedSetDuplicate();
            }
        }
    }

    /**
     * Met à jour les lignes existantes sur place, ajoute les nouvelles et retire celles qui ne sont plus envoyées :
     * remplacer tout casserait l'unicité (réalisation, série prévue), Hibernate insérant avant de supprimer.
     */
    private static void applySets(SessionPerformance performance, List<PerformedSetInput> inputs,
                                  Map<UUID, SessionSnapshotSet> plannedSets) {
        Map<UUID, PerformedSetInput> byId = inputs.stream()
                .collect(Collectors.toMap(PerformedSetInput::snapshotSetId, Function.identity()));
        performance.getSets().removeIf(set -> !byId.containsKey(set.getSnapshotSet().getId()));
        Map<UUID, PerformedSet> existing = performance.getSets().stream()
                .collect(Collectors.toMap(set -> set.getSnapshotSet().getId(), Function.identity()));
        for (PerformedSetInput input : inputs) {
            // Une charge sans répétitions n'a pas de sens : la série est non faite, sa charge est ignorée.
            BigDecimal load = input.reps() == null ? null : normalizeLoad(input.loadKg());
            PerformedSet set = existing.get(input.snapshotSetId());
            if (set == null) {
                performance.addSet(PerformedSet.builder()
                        .snapshotSet(plannedSets.get(input.snapshotSetId()))
                        .reps(input.reps())
                        .loadKg(load)
                        .build());
            } else {
                set.setReps(input.reps());
                set.setLoadKg(load);
            }
        }
    }

    private static BigDecimal normalizeLoad(BigDecimal loadKg) {
        return loadKg == null ? null : loadKg.setScale(1, java.math.RoundingMode.UNNECESSARY);
    }

    private static String normalize(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.strip();
    }

    private static AthletePerformanceResponse toResponse(SessionPerformance performance) {
        List<PerformedSetValue> sets = performance.getSets().stream()
                .map(set -> new PerformedSetValue(set.getSnapshotSet().getId(), set.getReps(), set.getLoadKg()))
                .toList();
        return new AthletePerformanceResponse(performance.getStatus(), performance.getRpe(),
                performance.getFatigue(), performance.getComment(), performance.getValidatedAt(),
                performance.getCorrectedAt(), sets);
    }
}
