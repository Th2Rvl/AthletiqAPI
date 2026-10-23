package com.athletiq.workout;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Question de propriété posée par l'{@code AccessGuard} : cette séance appartient-elle à ce coach ?
 * Séparé de {@link WorkoutService} (qui appelle l'AccessGuard) pour éviter une dépendance circulaire,
 * et sans dépendance vers {@code exercise}. Ne fait aucun contrôle d'accès et ne renvoie qu'un booléen.
 */
@Service
@RequiredArgsConstructor
public class WorkoutOwnershipService {

    private final WorkoutRepository workoutRepository;

    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID workoutId, UUID coachId) {
        return workoutRepository.existsByIdAndCoachId(workoutId, coachId);
    }
}
