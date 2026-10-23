package com.athletiq.workout;

import com.athletiq.exercise.ExerciseUsage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fournit à {@code exercise} les usages d'un exercice, sans que {@code exercise} dépende de {@code workout}. */
@Component
@RequiredArgsConstructor
class ExerciseUsageAdapter implements ExerciseUsage {

    private final WorkoutExerciseRepository workoutExerciseRepository;

    @Override
    @Transactional(readOnly = true)
    public long countWorkoutsUsing(UUID exerciseId) {
        return workoutExerciseRepository.countWorkoutsUsing(exerciseId);
    }
}
