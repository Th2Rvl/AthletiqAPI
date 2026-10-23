package com.athletiq.exercise;

import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Question de propriété posée par l'{@code AccessGuard} : cet exercice appartient-il à ce coach ?
 * Séparé de {@link ExerciseService} pour éviter une dépendance circulaire avec l'AccessGuard.
 * Ne fait aucun contrôle d'accès et ne renvoie qu'un booléen.
 */
@Service
@RequiredArgsConstructor
public class ExerciseOwnershipService {

    private final ExerciseRepository exerciseRepository;

    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID exerciseId, UUID coachId) {
        return exerciseRepository.existsByIdAndCoachId(exerciseId, coachId);
    }

    /**
     * Vrai si tous ces exercices appartiennent à ce coach, en une seule requête (les doublons sont
     * permis : un même exercice peut figurer plusieurs fois dans une séance).
     */
    @Transactional(readOnly = true)
    public boolean ownsAll(UUID coachId, Collection<UUID> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return true;
        }
        Collection<UUID> distinct = exerciseIds.stream().distinct().toList();
        return exerciseRepository.countByCoachIdAndIdIn(coachId, distinct) == distinct.size();
    }
}
