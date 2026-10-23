package com.athletiq.planning;

import com.athletiq.athlete.AthleteUnfollowedEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * P7 (RM10, Q9) : quand le lien coach-athlète prend fin, les séances à venir (aujourd'hui compris) et non
 * réalisées de ce coach pour cet athlète sont supprimées, puis les instantanés devenus orphelins. Le passé
 * et les séances des autres coachs restent. Exécuté avant le commit de la transaction du retrait : tout est
 * validé ensemble, ou rien. Nom distinct de {@code group.AthleteUnfollowedListener} (sinon collision de bean).
 */
@Component
@RequiredArgsConstructor
class PlanningAthleteUnfollowedListener {

    private final PlannedSessionRepository plannedSessionRepository;
    private final SessionSnapshotRepository snapshotRepository;
    private final PlanningClock planningClock;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAthleteUnfollowed(AthleteUnfollowedEvent event) {
        LocalDate today = planningClock.today();
        List<UUID> snapshotIds = plannedSessionRepository.findUpcomingSnapshotIds(event.coachId(), event.athleteId(),
                today);
        if (snapshotIds.isEmpty()) {
            return;
        }
        plannedSessionRepository.deleteUpcoming(event.coachId(), event.athleteId(), today);
        snapshotRepository.deleteOrphans(snapshotIds);
    }
}
