package com.athletiq.performance;

import com.athletiq.planning.PlannedSessionCompletion;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fournit à {@code planning} les séances réalisées (statut et verrouillage du calendrier, RM10), sans que
 * {@code planning} dépende de {@code performance}.
 */
@Component
@RequiredArgsConstructor
class PlannedSessionCompletionAdapter implements PlannedSessionCompletion {

    private final SessionPerformanceRepository performanceRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findValidated(Collection<UUID> plannedSessionIds) {
        if (plannedSessionIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(performanceRepository.findValidatedPlannedSessionIds(plannedSessionIds));
    }
}
