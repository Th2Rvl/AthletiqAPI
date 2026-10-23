package com.athletiq.planning;

import com.athletiq.exercise.PlannedExerciseUsage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fournit à {@code exercise} les usages d'un exercice dans les séances planifiées (Q11), sans dépendance inverse. */
@Component
@RequiredArgsConstructor
class PlannedExerciseUsageAdapter implements PlannedExerciseUsage {

    private final PlannedSessionRepository plannedSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public long countPlannedSessionsUsing(UUID exerciseId) {
        return plannedSessionRepository.countUsingExercise(exerciseId);
    }
}
