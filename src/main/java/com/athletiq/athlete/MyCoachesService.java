package com.athletiq.athlete;

import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coachs de l'athlète connecté : consultation (#8, US05) et départ d'un coach (#8, US05). */
@Service
@RequiredArgsConstructor
public class MyCoachesService {

    private final CoachAthleteRepository linkRepository;
    private final AthleteMapper mapper;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Coachs qui suivent l'athlète. Une liste vide signifie qu'il ne peut pas encore utiliser l'application (US06). */
    @Transactional(readOnly = true)
    public List<CoachLinkResponse> list() {
        accessGuard.assertAthlete();
        return linkRepository.findActiveByAthleteId(currentUser.id()).stream()
                .map(mapper::toCoachLinkResponse)
                .toList();
    }

    /**
     * Quitte un coach : le lien est clôturé, pas supprimé, comme quand c'est le coach qui retire
     * l'athlète. Le même événement retire l'athlète des groupes de ce coach et annule ses séances à venir.
     */
    @Transactional
    public void leave(UUID coachId) {
        accessGuard.assertAthlete();
        UUID athleteId = currentUser.id();
        CoachAthlete link = linkRepository.findByCoachIdAndAthleteIdAndEndedAtIsNull(coachId, athleteId)
                .orElseThrow(AthleteErrors::coachLinkNotFound);
        link.setEndedAt(clock.instant());
        linkRepository.flush();
        events.publishEvent(new AthleteUnfollowedEvent(coachId, athleteId));
    }
}
