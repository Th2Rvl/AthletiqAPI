package com.athletiq.planning;

import com.athletiq.athlete.AthleteLinkService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Question de propriété posée par l'{@code AccessGuard} : cette séance planifiée appartient-elle à ce coach,
 * qui suit toujours l'athlète (RM1, RM2) ? Une séance d'un athlète au lien clos devient donc invisible.
 * Séparé de {@link PlanningService} (qui appelle l'AccessGuard) pour éviter une dépendance circulaire ;
 * ne fait aucun contrôle d'accès et ne renvoie qu'un booléen.
 */
@Service
@RequiredArgsConstructor
public class PlannedSessionOwnershipService {

    private final PlannedSessionRepository plannedSessionRepository;
    private final AthleteLinkService athleteLinkService;

    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID plannedSessionId, UUID coachId) {
        return plannedSessionRepository.findAthleteIdByIdAndCoachId(plannedSessionId, coachId)
                .map(athleteId -> athleteLinkService.isFollowedBy(coachId, athleteId))
                .orElse(false);
    }
}
