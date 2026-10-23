package com.athletiq.exercise;

import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.exercise.dto.CreateExerciseRequest;
import com.athletiq.exercise.dto.ExerciseResponse;
import com.athletiq.exercise.dto.UpdateExerciseRequest;
import com.athletiq.user.UserService;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
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

    /** Caractère d'échappement des motifs LIKE (ni {@code \} ni un joker). */
    static final char LIKE_ESCAPE = '!';

    private final ExerciseRepository exerciseRepository;
    private final ExerciseMapper mapper;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;

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
     * Suppression directe. Le blocage par les séances planifiées viendra avec le ticket des séances.
     */
    @Transactional
    public void delete(UUID exerciseId) {
        accessGuard.assertExerciseOwner(exerciseId);
        exerciseRepository.delete(findOwnExercise(exerciseId));
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
            String message = ex.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(UK_EXERCISE_COACH_NAME)) {
                throw ExerciseErrors.exerciseNameAlreadyExists();
            }
            throw ex;
        }
    }

    /** {@code %q%} échappé, ou {@code null} si la recherche est vide. */
    static String likePattern(String query) {
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
