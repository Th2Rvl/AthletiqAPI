package com.athletiq.group;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Question de propriété posée par l'{@code AccessGuard} : ce groupe appartient-il à ce coach ?
 *
 * <p>Séparé de {@link GroupService} parce que ce dernier appelle l'{@code AccessGuard} : si le guard
 * dépendait de {@link GroupService}, les deux beans dépendraient l'un de l'autre. Ce service ne fait
 * aucun contrôle d'accès et ne renvoie qu'un booléen.
 */
@Service
@RequiredArgsConstructor
public class GroupOwnershipService {

    private final AthleteGroupRepository groupRepository;

    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID groupId, UUID coachId) {
        return groupRepository.existsByIdAndCoachId(groupId, coachId);
    }
}
