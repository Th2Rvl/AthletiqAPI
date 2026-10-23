package com.athletiq.exercise;

import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.dto.CreateExerciseRequest;
import com.athletiq.exercise.dto.ExerciseResponse;
import com.athletiq.exercise.dto.UpdateExerciseRequest;
import com.athletiq.user.UserService;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
 * Bibliothèque d'exercices du coach connecté (US15-US17). Chaque méthode commence par l'
 * {@code AccessGuard} (rôle COACH, puis propriété : 404 sinon), et les requêtes restent filtrées par coach.
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    static final String UK_EXERCISE_COACH_NAME = "uk_exercise_coach_name";
    static final String FK_WORKOUT_EXERCISE_EXERCISE = "fk_workout_exercise_exercise";

    /** Caractère d'échappement des motifs LIKE (ni {@code \} ni un joker). */
    static final char LIKE_ESCAPE = '!';

    private final ExerciseRepository exerciseRepository;
    private final ExerciseMapper mapper;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final ExerciseUsage exerciseUsage;

    /**
     * Exercices du coach, triés par nom. {@code q} cherche dans le nom sans tenir compte de la casse ;
     * {@code muscleGroup} et {@code equipment} sont des filtres optionnels.
     */
    @Transactional(readOnly = true)
    public Page<ExerciseResponse> list(String q, MuscleGroup muscleGroup, String equipment, Pageable pageable) {
        accessGuard.assertCoach();
        return exerciseRepository.search(currentUser.id(), likePattern(q), muscleGroup, blankToNull(equipment),
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ExerciseResponse get(UUID exerciseId) {
        accessGuard.assertExerciseOwner(exerciseId);
        return mapper.toResponse(findOwnExercise(exerciseId));
    }

    /** Le nom est unique par coach (RM : contrainte en base, vérifiée aussi avant l'écriture pour un message précis). */
    @Transactional
    public ExerciseResponse create(CreateExerciseRequest request) {
        accessGuard.assertCoach();
        UUID coachId = currentUser.id();
        if (exerciseRepository.existsByCoachIdAndName(coachId, request.name())) {
            throw ExerciseErrors.exerciseNameAlreadyExists();
        }
        Exercise exercise = Exercise.builder()
                .coach(userService.getReference(coachId))
                .name(request.name())
                .description(request.description())
                .muscleGroup(request.muscleGroup())
                .equipment(request.equipment())
                .videoUrl(request.videoUrl())
                .build();
        return mapper.toResponse(saveAndFlush(() -> exerciseRepository.saveAndFlush(exercise)));
    }

    /**
     * Remplace l'exercice entier (PUT) : un champ facultatif absent ou vide est effacé, pas conservé.
     */
    @Transactional
    public ExerciseResponse update(UUID exerciseId, UpdateExerciseRequest request) {
        accessGuard.assertExerciseOwner(exerciseId);
        UUID coachId = currentUser.id();
        Exercise exercise = findOwnExercise(exerciseId);
        if (exerciseRepository.existsByCoachIdAndNameAndIdNot(coachId, request.name(), exerciseId)) {
            throw ExerciseErrors.exerciseNameAlreadyExists();
        }
        exercise.setName(request.name());
        exercise.setDescription(request.description());
        exercise.setMuscleGroup(request.muscleGroup());
        exercise.setEquipment(request.equipment());
        exercise.setVideoUrl(request.videoUrl());
        return mapper.toResponse(saveAndFlush(() -> exerciseRepository.saveAndFlush(exercise)));
    }

    /**
     * Un exercice utilisé dans au moins une séance du coach ne se supprime pas (RM9) : 409
     * {@code EXERCISE_IN_USE} avec le nombre de séances. La clé étrangère {@code RESTRICT} protège en
     * dernier recours (séance enregistrée entre le comptage et la suppression) : même erreur.
     */
    @Transactional
    public void delete(UUID exerciseId) {
        accessGuard.assertExerciseOwner(exerciseId);
        Exercise exercise = findOwnExercise(exerciseId);
        long workoutCount = exerciseUsage.countWorkoutsUsing(exerciseId);
        if (workoutCount > 0) {
            throw ExerciseErrors.exerciseInUse(workoutCount);
        }
        try {
            exerciseRepository.delete(exercise);
            exerciseRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (mentions(ex, FK_WORKOUT_EXERCISE_EXERCISE)) {
                throw ExerciseErrors.exerciseInUse(Math.max(1, exerciseUsage.countWorkoutsUsing(exerciseId)));
            }
            throw ex;
        }
    }

    /**
     * Exercices du coach connecté parmi ces identifiants, indexés par id, en une requête. Pour les
     * séances, qui y font référence : un id inconnu ou d'un autre coach est simplement absent de la map
     * (la propriété est vérifiée avant par {@code AccessGuard.assertExercisesOwner}).
     */
    @Transactional(readOnly = true)
    public Map<UUID, Exercise> findOwned(Collection<UUID> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        return exerciseRepository.findAllByCoachIdAndIdIn(currentUser.id(), exerciseIds.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
    }

    // ---------------------------------------------------------------- interne

    /** Le filtre sur le coach reste dans la requête, même après l'{@code AccessGuard}. */
    private Exercise findOwnExercise(UUID exerciseId) {
        return exerciseRepository.findByIdAndCoachId(exerciseId, currentUser.id())
                .orElseThrow(ExerciseErrors::exerciseNotFound);
    }

    /** La contrainte unique protège en dernier recours (deux écritures simultanées) : même erreur métier. */
    private static <T> T saveAndFlush(Supplier<T> save) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException ex) {
            if (mentions(ex, UK_EXERCISE_COACH_NAME)) {
                throw ExerciseErrors.exerciseNameAlreadyExists();
            }
            throw ex;
        }
    }

    private static boolean mentions(DataIntegrityViolationException ex, String constraint) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraint);
    }

    /**
     * {@code %q%} échappé (caractère {@link #LIKE_ESCAPE}), ou {@code null} si la recherche est vide.
     * Public pour que les séances cherchent par nom exactement de la même façon.
     */
    public static String likePattern(String query) {
        String stripped = blankToNull(query);
        if (stripped == null) {
            return null;
        }
        StringBuilder pattern = new StringBuilder("%");
        for (char c : stripped.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
