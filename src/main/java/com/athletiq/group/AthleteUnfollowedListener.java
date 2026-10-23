package com.athletiq.group;

import com.athletiq.athlete.AthleteUnfollowedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RM5 : un athlète que le coach ne suit plus quitte tous les groupes de ce coach. Exécuté avant le
 * commit de la transaction du retrait : le lien clôturé et les appartenances supprimées sont validés
 * ensemble, ou pas du tout.
 */
@Component
@RequiredArgsConstructor
class AthleteUnfollowedListener {

    private final AthleteGroupMemberRepository memberRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAthleteUnfollowed(AthleteUnfollowedEvent event) {
        memberRepository.deleteByCoachIdAndAthleteId(event.coachId(), event.athleteId());
    }
}
