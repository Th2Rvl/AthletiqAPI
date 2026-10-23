package com.athletiq.workout;

import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.Exercise;
import com.athletiq.exercise.ExerciseErrors;
import com.athletiq.exercise.ExerciseService;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.user.UserService;
import com.athletiq.workout.dto.PlannedSetRequest;
import com.athletiq.workout.dto.WorkoutExerciseRequest;
import com.athletiq.workout.dto.WorkoutRequest;
import com.athletiq.workout.dto.WorkoutResponse;
import com.athletiq.workout.dto.WorkoutSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Séances du coach connecté (US18-US20). Chaque méthode commence par l'{@code AccessGuard} (rôle COACH,
 * puis propriété de la séance et des exercices référencés : 404 sinon), et les requêtes restent filtrées
 * par coach. Création, remplacement et duplication sont atomiques (RM6) : un refus n'écrit rien.
 */
@Service
@RequiredArgsConstructor
public class WorkoutService {

    static final String UK_WORKOUT_COACH_NAME = "uk_workout_coach_name";
    static final int NAME_MAX_LENGTH = 100;

    private final WorkoutRepository workoutRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutMapper mapper;
    private final ExerciseService exerciseService;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;

    /**
     * Séances du coach, de la plus récemment modifiée à la plus ancienne (tri fixe). {@code q} cherche
     * dans le nom sans tenir compte de la casse ni des accents. Deux requêtes, quelle que soit la taille
     * de la page : la page, puis les groupes musculaires de ses séances.
     */
    @Transactional(readOnly = true)
    public Page<WorkoutSummaryResponse> list(String q, Pageable pageable) {
        accessGuard.assertCoach();
        Page<Workout> page = workoutRepository.search(currentUser.id(), ExerciseService.likePattern(q),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        List<UUID> ids = page.getContent().stream().map(Workout::getId).toList();
        Map<UUID, List<WorkoutMuscleGroupCount>> counts = ids.isEmpty()
                ? Map.of()
                : workoutExerciseRepository.countByMuscleGroup(ids).stream()
                .collect(Collectors.groupingBy(WorkoutMuscleGroupCount::workoutId));
        return page.map(workout -> summary(workout, counts.getOrDefault(workout.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public WorkoutResponse get(UUID workoutId) {
        accessGuard.assertWorkoutOwner(workoutId);
        return mapper.toResponse(loadDetail(workoutId));
    }

    /** Contrôles dans l'ordre du contrat : exercices du coach (404), puis nom libre (409). */
    @Transactional
    public WorkoutResponse create(WorkoutRequest request) {
        accessGuard.assertCoach();
        List<UUID> exerciseIds = exerciseIds(request);
        accessGuard.assertExercisesOwner(exerciseIds);
        UUID coachId = currentUser.id();
        if (workoutRepository.existsByCoachIdAndName(coachId, request.name())) {
            throw WorkoutErrors.workoutNameAlreadyExists();
        }
        Map<UUID, Exercise> exercises = exerciseService.findOwned(exerciseIds);
        Workout workout = Workout.builder()
                .coach(userService.getReference(coachId))
                .name(request.name())
                .description(request.description())
                .build();
        addExercises(workout, request.exercises(), exercises);
        return mapper.toResponse(writeName(() -> workoutRepository.saveAndFlush(workout), true));
    }

    /**
     * Remplace toute la séance (PUT, Q9) : nom, description, exercices et séries. Garder son propre nom
     * est accepté. Contrôles dans l'ordre : séance (404), exercices (404), nom (409).
     */
    @Transactional
    public WorkoutResponse update(UUID workoutId, WorkoutRequest request) {
        accessGuard.assertWorkoutOwner(workoutId);
        List<UUID> exerciseIds = exerciseIds(request);
        accessGuard.assertExercisesOwner(exerciseIds);
        UUID coachId = currentUser.id();
        Workout workout = loadDetail(workoutId);
        if (workoutRepository.existsByCoachIdAndNameAndIdNot(coachId, request.name(), workoutId)) {
            throw WorkoutErrors.workoutNameAlreadyExists();
        }
        Map<UUID, Exercise> exercises = exerciseService.findOwned(exerciseIds);
        return mapper.toResponse(writeName(() -> {
            workout.setName(request.name());
            workout.setDescription(request.description());
            workout.getExercises().clear();
            // Hibernate insère avant de supprimer : sans ce flush, les nouvelles lignes violeraient
            // uk_workout_exercise_position avec les anciennes.
            workoutRepository.flush();
            addExercises(workout, request.exercises(), exercises);
            workout.setRevision(workout.getRevision() + 1);
            return workoutRepository.saveAndFlush(workout);
        }, true));
    }

    /**
     * Copie profonde (RM7) pour le même coach, sans lien avec l'original, nommée « nom (copie) », puis
     * « nom (copie 2) »… selon le premier nom libre (Q6).
     */
    @Transactional
    public WorkoutResponse duplicate(UUID workoutId) {
        accessGuard.assertWorkoutOwner(workoutId);
        UUID coachId = currentUser.id();
        Workout source = loadDetail(workoutId);
        Workout copy = Workout.builder()
                .coach(userService.getReference(coachId))
                .name(copyName(coachId, source.getName()))
                .description(source.getDescription())
                .build();
        source.getExercises().stream()
                .sorted(Comparator.comparingInt(WorkoutExercise::getPosition))
                .forEach(original -> {
                    WorkoutExercise exercise = WorkoutExercise.builder()
                            .exercise(original.getExercise())
                            .note(original.getNote())
                            .build();
                    original.getSets().stream()
                            .sorted(Comparator.comparingInt(PlannedSet::getPosition))
                            .forEach(set -> exercise.addSet(PlannedSet.builder()
                                    .reps(set.getReps())
                                    .loadKg(set.getLoadKg())
                                    .restSeconds(set.getRestSeconds())
                                    .build()));
                    copy.addExercise(exercise);
                });
        return mapper.toResponse(writeName(() -> workoutRepository.saveAndFlush(copy), false));
    }

    /**
     * Suppression physique (RM8) : la base supprime exercices de séance et séries en cascade. Les
     * planifications, qui copient la séance (Q5, Q7), ne sont pas touchées.
     */
    @Transactional
    public void delete(UUID workoutId) {
        accessGuard.assertWorkoutOwner(workoutId);
        if (workoutRepository.deleteByIdAndCoachId(workoutId, currentUser.id()) == 0) {
            throw WorkoutErrors.workoutNotFound();
        }
    }

    // ---------------------------------------------------------------- interne

    /**
     * Charge la séance du coach courant avec ses exercices, leurs exercices de bibliothèque et leurs
     * séries, en deux requêtes (pas de N+1). Le filtre sur le coach reste dans la requête.
     */
    private Workout loadDetail(UUID workoutId) {
        Workout workout = workoutRepository.findDetailByIdAndCoachId(workoutId, currentUser.id())
                .orElseThrow(WorkoutErrors::workoutNotFound);
        if (!workout.getExercises().isEmpty()) {
            workoutExerciseRepository.fetchSetsOfWorkout(workoutId);
        }
        return workout;
    }

    private static List<UUID> exerciseIds(WorkoutRequest request) {
        return request.exercises().stream().map(WorkoutExerciseRequest::exerciseId).toList();
    }

    /** La position de chaque exercice et de chaque série est son index dans la liste reçue. */
    private static void addExercises(Workout workout, List<WorkoutExerciseRequest> requests,
                                     Map<UUID, Exercise> exercises) {
        for (WorkoutExerciseRequest request : requests) {
            Exercise exercise = exercises.get(request.exerciseId());
            if (exercise == null) {
                // Supprimé entre la vérification de propriété et la lecture.
                throw ExerciseErrors.exerciseNotFound();
            }
            WorkoutExercise workoutExercise = WorkoutExercise.builder()
                    .exercise(exercise)
                    .note(request.note())
                    .build();
            for (PlannedSetRequest set : request.sets()) {
                workoutExercise.addSet(PlannedSet.builder()
                        .reps(set.reps())
                        .loadKg(normalizeLoad(set.loadKg()))
                        .restSeconds(set.restSeconds())
                        .build());
            }
            workout.addExercise(workoutExercise);
        }
    }

    /** Même échelle qu'en base ({@code DECIMAL(5,1)}) : la réponse d'écriture est identique à une relecture. */
    private static BigDecimal normalizeLoad(BigDecimal loadKg) {
        return loadKg == null ? null : loadKg.setScale(1, RoundingMode.UNNECESSARY);
    }

    /**
     * Premier nom libre parmi « nom (copie) », « nom (copie 2) »… Le nom source est tronqué pour que la
     * copie tienne en {@value #NAME_MAX_LENGTH} caractères, et pris tel quel (« A (copie) » donne
     * « A (copie) (copie) »).
     */
    String copyName(UUID coachId, String sourceName) {
        for (int n = 1; ; n++) {
            String candidate = copyCandidate(sourceName, n);
            if (!workoutRepository.existsByCoachIdAndName(coachId, candidate)) {
                return candidate;
            }
        }
    }

    static String copyCandidate(String sourceName, int n) {
        String suffix = n == 1 ? " (copie)" : " (copie " + n + ")";
        int room = NAME_MAX_LENGTH - suffix.length();
        String base = sourceName;
        if (base.length() > room) {
            int cut = Character.isHighSurrogate(base.charAt(room - 1)) ? room - 1 : room;
            base = base.substring(0, cut);
        }
        return base.strip() + suffix;
    }

    private WorkoutSummaryResponse summary(Workout workout, List<WorkoutMuscleGroupCount> counts) {
        long exerciseCount = counts.stream().mapToLong(WorkoutMuscleGroupCount::count).sum();
        List<MuscleGroup> muscleGroups = counts.stream()
                .map(WorkoutMuscleGroupCount::muscleGroup)
                .distinct()
                .sorted()
                .toList();
        return mapper.toSummary(workout, exerciseCount, muscleGroups);
    }

    /**
     * La contrainte unique sur le nom protège en dernier recours (deux écritures simultanées) : même
     * erreur métier, avec le champ {@code name} sauf pour une copie, dont le nom n'a pas été saisi.
     */
    private static <T> T writeName(Supplier<T> write, boolean nameWasEntered) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(UK_WORKOUT_COACH_NAME)) {
                throw nameWasEntered ? WorkoutErrors.workoutNameAlreadyExists() : WorkoutErrors.copyNameAlreadyExists();
            }
            throw ex;
        }
    }
}
