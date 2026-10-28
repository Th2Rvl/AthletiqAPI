package com.athletiq.athlete;

import com.athletiq.athlete.dto.AthleteRef;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Questions de propriété sur les liens coach ↔ athlète et les invitations, posées par
 * l'{@code AccessGuard} et par les autres fonctionnalités (ex. {@code group}), qui ne touchent jamais
 * aux repositories de {@code athlete}.
 *
 * <p>Ce service ne fait lui-même aucun contrôle d'accès et ne dépend pas de l'{@code AccessGuard} :
 * c'est ce qui permet au guard de l'appeler sans dépendance circulaire. Il ne renvoie que des
 * booléens, des compteurs et des identités, jamais d'entité.
 */
@Service
@RequiredArgsConstructor
public class AthleteLinkService {

    private final CoachAthleteRepository linkRepository;
    private final CoachInvitationRepository invitationRepository;
    private final Clock clock;

    /**
     * Athlètes suivis activement par ce coach, triés par nom puis prénom ; seulement les membres de ce groupe
     * s'il est donné. Sans contrôle d'accès : l'appelant a vérifié le rôle et la propriété du groupe.
     */
    @Transactional(readOnly = true)
    public List<AthleteRef> listFollowed(UUID coachId, UUID groupId) {
        return linkRepository.findFollowedRefs(coachId, groupId);
    }

    /** Vrai si ce coach suit activement cet athlète. */
    @Transactional(readOnly = true)
    public boolean isFollowedBy(UUID coachId, UUID athleteId) {
        return linkRepository.existsByCoachIdAndAthleteIdAndEndedAtIsNull(coachId, athleteId);
    }

    /** Vrai si ce coach suit activement chacun de ces athlètes (une seule requête de comptage). */
    @Transactional(readOnly = true)
    public boolean followsAll(UUID coachId, Collection<UUID> athleteIds) {
        if (athleteIds.isEmpty()) {
            return true;
        }
        long distinct = athleteIds.stream().distinct().count();
        return linkRepository.countActiveByCoachIdAndAthleteIds(coachId, athleteIds) == distinct;
    }

    /** Vrai si cette invitation appartient à ce coach et est encore en attente. */
    @Transactional(readOnly = true)
    public boolean isPendingInvitationOf(UUID coachId, UUID invitationId) {
        return invitationRepository.findPendingByIdAndCoachId(invitationId, coachId, clock.instant()).isPresent();
    }
}
