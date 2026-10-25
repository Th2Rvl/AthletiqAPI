package com.athletiq.planning;

import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.group.GroupMemberService;
import com.athletiq.group.dto.GroupMemberRef;
import com.athletiq.planning.dto.AthleteCalendarResponse;
import com.athletiq.planning.dto.CreatePlannedSessionRequest;
import com.athletiq.planning.dto.CreatedCountResponse;
import com.athletiq.planning.dto.DuplicateWeekRequest;
import com.athletiq.planning.dto.GroupCalendarEntry;
import com.athletiq.planning.dto.GroupCalendarResponse;
import com.athletiq.planning.dto.GroupCalendarSession;
import com.athletiq.planning.dto.MovePlannedSessionRequest;
import com.athletiq.planning.dto.PlannedSessionDetailResponse;
import com.athletiq.planning.dto.PlannedSessionSummary;
import com.athletiq.planning.dto.UpcomingCountResponse;
import com.athletiq.user.User;
import com.athletiq.user.UserName;
import com.athletiq.user.UserService;
import com.athletiq.workout.WorkoutService;
import com.athletiq.workout.dto.WorkoutResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planification des séances du coach connecté (US21-US26). Chaque méthode commence par l'{@code AccessGuard}
 * (rôle COACH, athlète suivi, groupe, séance ou séance planifiée du coach : 404 sinon), et chaque requête
 * reste filtrée par {@code coach_id} (RM1) : les séances d'un autre coach n'apparaissent jamais, même dans
 * les compteurs. Plusieurs séances par athlète et par jour sont permises (Q1) : aucun conflit de date.
 *
 * <p>« Aujourd'hui » vient de {@link PlanningClock} (Europe/Paris, Q12) : une date cible antérieure est
 * refusée (Q5), une séance passée est verrouillée (Q6). Une séance réalisée (réalisation validée, #7) l'est
 * aussi, même future (RM10 de #7), et chaque séance affichée porte son {@link SessionStatus} : les
 * réalisations viennent de {@link PlannedSessionCompletion}, en une requête par écran. Groupe et
 * duplication sont tout-ou-rien (RM9) : tous les contrôles passent avant la première écriture, dans une
 * seule transaction.
 */
@Service
@RequiredArgsConstructor
public class PlanningService {

    /** RM13 : une requête de calendrier couvre au plus 12 semaines, bornes incluses. */
    static final int MAX_PERIOD_DAYS = 84;
    /** Au-delà, une planification de groupe ou une duplication est refusée (409), rien n'est créé. */
    static final int MAX_BATCH_SIZE = 500;

    private final PlannedSessionRepository plannedSessionRepository;
    private final SessionSnapshotRepository snapshotRepository;
    private final SessionSnapshotExerciseRepository snapshotExerciseRepository;
    private final PlanningMapper mapper;
    private final WorkoutService workoutService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final PlanningClock planningClock;
    private final PlannedSessionCompletion completion;

    // ---------------------------------------------------------------- calendriers

    /** US24 : séances de ce coach pour cet athlète, de {@code from} à {@code to} inclus. */
    @Transactional(readOnly = true)
    public AthleteCalendarResponse athleteCalendar(UUID athleteId, LocalDate from, LocalDate to) {
        assertPeriod(from, to);
        accessGuard.assertCoachOfAthlete(athleteId);
        LocalDate today = planningClock.today();
        List<PlannedSessionRow> rows = plannedSessionRepository.findAthleteRows(currentUser.id(), athleteId, from, to);
        Map<UUID, SnapshotCount> counts = countsOf(rows);
        Set<UUID> done = doneAmong(rows);
        List<PlannedSessionSummary> sessions = rows.stream()
                .map(row -> {
                    SnapshotCount count = counts.get(row.snapshotId());
                    boolean isDone = done.contains(row.id());
                    return new PlannedSessionSummary(row.id(), row.athleteId(), row.date(), row.name(),
                            count == null ? 0 : count.exerciseCount(), count == null ? 0 : count.setCount(),
                            row.groupId(), PlannedSessionRules.isLocked(row.date(), today, isDone),
                            SessionStatus.of(row.date(), today, isDone));
                })
                .toList();
        return new AthleteCalendarResponse(athleteId, from, to, today, sessions);
    }

    /**
     * US24, vue groupe (Q8) : séances de ce coach planifiées via ce groupe pour ses membres actuels,
     * regroupées par jour et instantané (« nom, n/N athlètes »). Une séance individuelle d'un membre
     * n'apparaît que dans le calendrier de l'athlète ; un ancien membre n'apparaît plus.
     */
    @Transactional(readOnly = true)
    public GroupCalendarResponse groupCalendar(UUID groupId, LocalDate from, LocalDate to) {
        assertPeriod(from, to);
        accessGuard.assertGroupOwner(groupId);
        LocalDate today = planningClock.today();
        List<GroupMemberRef> members = groupMemberService.listAllMembers(groupId);
        if (members.isEmpty()) {
            return new GroupCalendarResponse(groupId, from, to, today, 0, List.of());
        }
        Map<UUID, Integer> rank = new HashMap<>();
        Map<UUID, GroupMemberRef> byId = new HashMap<>();
        for (int i = 0; i < members.size(); i++) {
            rank.put(members.get(i).athleteId(), i);
            byId.put(members.get(i).athleteId(), members.get(i));
        }
        List<PlannedSessionRow> rows = plannedSessionRepository.findGroupRows(currentUser.id(), groupId,
                byId.keySet(), from, to);
        Set<UUID> done = doneAmong(rows);

        record Key(LocalDate date, UUID snapshotId) {
        }
        Map<Key, List<PlannedSessionRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> new Key(row.date(), row.snapshotId()), LinkedHashMap::new,
                        Collectors.toList()));
        List<GroupCalendarEntry> entries = new ArrayList<>();
        grouped.forEach((key, sessions) -> {
            List<GroupCalendarSession> calendarSessions = sessions.stream()
                    .sorted(Comparator.comparingInt((PlannedSessionRow row) -> rank.get(row.athleteId()))
                            .thenComparing(PlannedSessionRow::createdAt)
                            .thenComparing(PlannedSessionRow::id))
                    .map(row -> {
                        GroupMemberRef member = byId.get(row.athleteId());
                        return new GroupCalendarSession(row.id(), row.athleteId(), member.firstName(),
                                member.lastName(), SessionStatus.of(row.date(), today, done.contains(row.id())));
                    })
                    .toList();
            int athleteCount = (int) sessions.stream().map(PlannedSessionRow::athleteId).distinct().count();
            // L'entrée n'est verrouillée en entier que si chacune de ses séances l'est (jour passé ou toutes réalisées).
            boolean allDone = sessions.stream().allMatch(row -> done.contains(row.id()));
            entries.add(new GroupCalendarEntry(key.date(), key.snapshotId(), sessions.getFirst().name(),
                    athleteCount, members.size(), PlannedSessionRules.isLocked(key.date(), today, allDone),
                    calendarSessions));
        });
        entries.sort(Comparator.comparing(GroupCalendarEntry::date)
                .thenComparing(GroupCalendarEntry::name)
                .thenComparing(GroupCalendarEntry::snapshotId));
        return new GroupCalendarResponse(groupId, from, to, today, members.size(), entries);
    }

    /**
     * Nombre de séances que supprimerait le retrait de cet athlète (même définition que P7) : à venir,
     * aujourd'hui compris, sans réalisation validée (#7).
     */
    @Transactional(readOnly = true)
    public UpcomingCountResponse upcomingCount(UUID athleteId) {
        accessGuard.assertCoachOfAthlete(athleteId);
        return new UpcomingCountResponse(plannedSessionRepository.countUpcoming(currentUser.id(), athleteId,
                planningClock.today()));
    }

    // ---------------------------------------------------------------- planifier

    /**
     * US21 : un instantané neuf de la séance (RM5), placé à {@code date} pour cet athlète. Contrôles dans
     * l'ordre du contrat : athlète suivi (404), séance du coach (404), date non passée (409).
     */
    @Transactional
    public PlannedSessionDetailResponse planForAthlete(UUID athleteId, CreatePlannedSessionRequest request) {
        accessGuard.assertCoachOfAthlete(athleteId);
        WorkoutResponse workout = workoutService.get(request.workoutId());
        LocalDate today = planningClock.today();
        if (request.date().isBefore(today)) {
            throw PlanningErrors.dateInPast();
        }
        User coach = userService.getReference(currentUser.id());
        SessionSnapshot snapshot = snapshotRepository.save(SessionSnapshot.copyOf(coach, workout));
        PlannedSession plannedSession = plannedSessionRepository.saveAndFlush(PlannedSession.builder()
                .coach(coach)
                .athlete(userService.getReference(athleteId))
                .scheduledDate(request.date())
                .snapshot(snapshot)
                .sourceWorkoutId(workout.id())
                .build());
        // Une séance qui vient d'être planifiée n'a pas encore de réalisation.
        return detail(plannedSession, snapshot, today, false);
    }

    /**
     * US22 (Q3) : un seul instantané partagé, et une séance planifiée par membre présent maintenant, avec
     * {@code group_id}. Un membre ajouté ensuite ne reçoit rien ; un membre retiré garde la sienne.
     */
    @Transactional
    public CreatedCountResponse planForGroup(UUID groupId, CreatePlannedSessionRequest request) {
        accessGuard.assertGroupOwner(groupId);
        WorkoutResponse workout = workoutService.get(request.workoutId());
        List<UUID> athleteIds = groupMemberService.listAllMembers(groupId).stream()
                .map(GroupMemberRef::athleteId)
                .toList();
        if (athleteIds.isEmpty()) {
            throw PlanningErrors.groupHasNoMembers();
        }
        if (request.date().isBefore(planningClock.today())) {
            throw PlanningErrors.dateInPast();
        }
        assertBatchSize(athleteIds.size());
        // Défense en profondeur : les membres d'un groupe sont suivis (RM8 de #3).
        accessGuard.assertCoachOfAthletes(athleteIds);
        User coach = userService.getReference(currentUser.id());
        SessionSnapshot snapshot = snapshotRepository.save(SessionSnapshot.copyOf(coach, workout));
        List<PlannedSession> created = athleteIds.stream()
                .map(athleteId -> PlannedSession.builder()
                        .coach(coach)
                        .athlete(userService.getReference(athleteId))
                        .scheduledDate(request.date())
                        .snapshot(snapshot)
                        .sourceWorkoutId(workout.id())
                        .groupId(groupId)
                        .build())
                .toList();
        plannedSessionRepository.saveAllAndFlush(created);
        return new CreatedCountResponse(created.size());
    }

    // ---------------------------------------------------------------- séance planifiée

    @Transactional(readOnly = true)
    public PlannedSessionDetailResponse get(UUID plannedSessionId) {
        accessGuard.assertPlannedSessionOwner(plannedSessionId);
        PlannedSession plannedSession = loadOwn(plannedSessionId);
        return detail(plannedSession, loadSnapshot(plannedSession), planningClock.today(), isDone(plannedSessionId));
    }

    /**
     * US25 : seule la date change (Q13), le contenu et le groupe restent. Contrôles dans l'ordre : séance
     * (404), verrouillée (409 : passée ou réalisée, même future), date cible passée (409). La même date
     * répond 200 sans rien changer.
     */
    @Transactional
    public PlannedSessionDetailResponse move(UUID plannedSessionId, MovePlannedSessionRequest request) {
        accessGuard.assertPlannedSessionOwner(plannedSessionId);
        PlannedSession plannedSession = loadOwn(plannedSessionId);
        LocalDate today = planningClock.today();
        if (PlannedSessionRules.isLocked(plannedSession.getScheduledDate(), today, isDone(plannedSessionId))) {
            throw PlanningErrors.plannedSessionLocked();
        }
        if (request.date().isBefore(today)) {
            throw PlanningErrors.dateInPast();
        }
        if (!request.date().equals(plannedSession.getScheduledDate())) {
            plannedSession.setScheduledDate(request.date());
            plannedSessionRepository.flush();
        }
        // Non verrouillée, donc non réalisée.
        return detail(plannedSession, loadSnapshot(plannedSession), today, false);
    }

    /**
     * US25, Q7 : annuler supprime la séance planifiée (le jour n'est pas compté comme manqué), puis son
     * instantané s'il n'est plus partagé. Une séance passée ou réalisée, même future, ne s'annule pas (409) ;
     * un brouillon de réalisation part avec sa séance (CASCADE).
     */
    @Transactional
    public void cancel(UUID plannedSessionId) {
        accessGuard.assertPlannedSessionOwner(plannedSessionId);
        PlannedSession plannedSession = loadOwn(plannedSessionId);
        if (PlannedSessionRules.isLocked(plannedSession.getScheduledDate(), planningClock.today(),
                isDone(plannedSessionId))) {
            throw PlanningErrors.plannedSessionLocked();
        }
        UUID snapshotId = plannedSession.getSnapshot().getId();
        if (plannedSessionRepository.deleteByIdAndCoachId(plannedSessionId, currentUser.id()) == 0) {
            throw PlanningErrors.plannedSessionNotFound();
        }
        snapshotRepository.deleteOrphans(List.of(snapshotId));
    }

    // ---------------------------------------------------------------- dupliquer

    /**
     * US26 (Q10), athlète : toutes les séances de ce coach pour cet athlète dans la semaine source, recopiées
     * le même jour de la semaine sur les {@code weeks} semaines suivantes. Chaque copie référence le
     * <em>même</em> instantané que sa source (le modèle actuel n'est pas relu) et garde groupe et modèle.
     */
    @Transactional
    public CreatedCountResponse duplicateWeekForAthlete(UUID athleteId, DuplicateWeekRequest request) {
        accessGuard.assertCoachOfAthlete(athleteId);
        UUID coachId = currentUser.id();
        List<PlannedSessionRow> sources = plannedSessionRepository.findAthleteRows(coachId, athleteId,
                request.weekStart(), weekEnd(request));
        if (sources.isEmpty()) {
            throw PlanningErrors.weekEmpty();
        }
        assertTargetsNotInPast(sources, request);
        assertBatchSize((long) sources.size() * request.weeks());
        User coach = userService.getReference(coachId);
        User athlete = userService.getReference(athleteId);
        List<PlannedSession> copies = new ArrayList<>();
        for (int week = 1; week <= request.weeks(); week++) {
            for (PlannedSessionRow source : sources) {
                copies.add(copy(coach, athlete, source, week, source.groupId()));
            }
        }
        plannedSessionRepository.saveAllAndFlush(copies);
        return new CreatedCountResponse(copies.size());
    }

    /**
     * US26 (Q10), groupe : les séances planifiées via ce groupe dans la semaine source, regroupées par
     * (jour, instantané), recopiées pour chaque membre <em>actuel</em> : {@code created = combinaisons ×
     * membres × semaines}.
     */
    @Transactional
    public CreatedCountResponse duplicateWeekForGroup(UUID groupId, DuplicateWeekRequest request) {
        accessGuard.assertGroupOwner(groupId);
        UUID coachId = currentUser.id();
        List<UUID> athleteIds = groupMemberService.listAllMembers(groupId).stream()
                .map(GroupMemberRef::athleteId)
                .toList();
        if (athleteIds.isEmpty()) {
            throw PlanningErrors.groupHasNoMembers();
        }
        // Une source par (jour, instantané), dans l'ordre du calendrier.
        Map<String, PlannedSessionRow> combinations = new LinkedHashMap<>();
        plannedSessionRepository.findGroupSourceRows(coachId, groupId, request.weekStart(), weekEnd(request))
                .forEach(row -> combinations.putIfAbsent(row.date() + "/" + row.snapshotId(), row));
        if (combinations.isEmpty()) {
            throw PlanningErrors.weekEmpty();
        }
        List<PlannedSessionRow> sources = List.copyOf(combinations.values());
        assertTargetsNotInPast(sources, request);
        assertBatchSize((long) sources.size() * athleteIds.size() * request.weeks());
        accessGuard.assertCoachOfAthletes(athleteIds);
        User coach = userService.getReference(coachId);
        List<PlannedSession> copies = new ArrayList<>();
        for (int week = 1; week <= request.weeks(); week++) {
            for (PlannedSessionRow source : sources) {
                for (UUID athleteId : athleteIds) {
                    copies.add(copy(coach, userService.getReference(athleteId), source, week, groupId));
                }
            }
        }
        plannedSessionRepository.saveAllAndFlush(copies);
        return new CreatedCountResponse(copies.size());
    }

    // ---------------------------------------------------------------- interne

    /** {@code from <= to} et au plus {@value #MAX_PERIOD_DAYS} jours, bornes incluses (RM13). */
    static void assertPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidParameterException("from");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            throw new InvalidParameterException("to");
        }
    }

    private static void assertBatchSize(long size) {
        if (size > MAX_BATCH_SIZE) {
            throw PlanningErrors.batchTooLarge();
        }
    }

    private static LocalDate weekEnd(DuplicateWeekRequest request) {
        return request.weekStart().plusDays(6);
    }

    /** La première cible est la plus proche : si elle est permise, toutes le sont (Q5). */
    private void assertTargetsNotInPast(List<PlannedSessionRow> sources, DuplicateWeekRequest request) {
        LocalDate firstSource = sources.stream().map(PlannedSessionRow::date).min(Comparator.naturalOrder())
                .orElse(request.weekStart());
        if (firstSource.plusWeeks(1).isBefore(planningClock.today())) {
            throw PlanningErrors.targetDateInPast();
        }
    }

    private PlannedSession copy(User coach, User athlete, PlannedSessionRow source, int week, UUID groupId) {
        return PlannedSession.builder()
                .coach(coach)
                .athlete(athlete)
                .scheduledDate(source.date().plusWeeks(week))
                .snapshot(snapshotRepository.getReferenceById(source.snapshotId()))
                .sourceWorkoutId(source.sourceWorkoutId())
                .groupId(groupId)
                .build();
    }

    /** Le filtre sur le coach reste dans la requête, même après l'{@code AccessGuard}. */
    private PlannedSession loadOwn(UUID plannedSessionId) {
        return plannedSessionRepository.findByIdAndCoachId(plannedSessionId, currentUser.id())
                .orElseThrow(PlanningErrors::plannedSessionNotFound);
    }

    /** Instantané, exercices puis séries : deux requêtes, sans produit cartésien ni N+1. */
    private SessionSnapshot loadSnapshot(PlannedSession plannedSession) {
        UUID snapshotId = plannedSession.getSnapshot().getId();
        SessionSnapshot snapshot = snapshotRepository.findDetailById(snapshotId)
                .orElseThrow(PlanningErrors::plannedSessionNotFound);
        if (!snapshot.getExercises().isEmpty()) {
            snapshotExerciseRepository.fetchSetsOfSnapshot(snapshotId);
        }
        return snapshot;
    }

    private PlannedSessionDetailResponse detail(PlannedSession plannedSession, SessionSnapshot snapshot,
                                                LocalDate today, boolean done) {
        UUID athleteId = plannedSession.getAthlete().getId();
        UserName athlete = userService.findNames(List.of(athleteId)).get(athleteId);
        LocalDate date = plannedSession.getScheduledDate();
        return mapper.toDetail(plannedSession, snapshot, athlete, PlannedSessionRules.isLocked(date, today, done),
                SessionStatus.of(date, today, done));
    }

    /** Vrai si cette séance planifiée a une réalisation validée (#7). */
    private boolean isDone(UUID plannedSessionId) {
        return completion.findValidated(List.of(plannedSessionId)).contains(plannedSessionId);
    }

    /** Séances réalisées parmi ces lignes de calendrier, en une requête pour tout l'écran. */
    private Set<UUID> doneAmong(List<PlannedSessionRow> rows) {
        if (rows.isEmpty()) {
            return Set.of();
        }
        return completion.findValidated(rows.stream().map(PlannedSessionRow::id).toList());
    }

    private Map<UUID, SnapshotCount> countsOf(List<PlannedSessionRow> rows) {
        Set<UUID> snapshotIds = rows.stream().map(PlannedSessionRow::snapshotId).collect(Collectors.toSet());
        if (snapshotIds.isEmpty()) {
            return Map.of();
        }
        return snapshotExerciseRepository.countBySnapshot(snapshotIds).stream()
                .collect(Collectors.toMap(SnapshotCount::snapshotId, Function.identity()));
    }
}
