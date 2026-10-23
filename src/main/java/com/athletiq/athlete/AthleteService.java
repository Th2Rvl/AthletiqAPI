package com.athletiq.athlete;

import com.athletiq.athlete.dto.AthleteResponse;
import com.athletiq.athlete.dto.GroupRef;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Athlètes suivis par le coach connecté : liste (US11) et retrait (US12). */
@Service
@RequiredArgsConstructor
public class AthleteService {

    /** Caractère d'échappement des motifs LIKE : ni {@code \} (littéral piégeux en MySQL) ni un joker. */
    static final char LIKE_ESCAPE = '!';

    private final CoachAthleteRepository linkRepository;
    private final AthleteGroupDirectory groupDirectory;
    private final AthleteMapper mapper;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Athlètes suivis activement par le coach, triés par nom puis prénom (D6). {@code query} cherche
     * dans le prénom, le nom et l'e-mail, sans tenir compte de la casse ni des accents ;
     * {@code groupId} et {@code excludeGroupId} (exclusifs) filtrent sur un groupe du coach.
     */
    @Transactional(readOnly = true)
    public Page<AthleteResponse> list(String query, UUID groupId, UUID excludeGroupId, Pageable pageable) {
        accessGuard.assertCoach();
        if (groupId != null && excludeGroupId != null) {
            throw new InvalidParameterException("excludeGroupId");
        }
        if (groupId != null) {
            accessGuard.assertGroupOwner(groupId);
        }
        if (excludeGroupId != null) {
            accessGuard.assertGroupOwner(excludeGroupId);
        }
        UUID coachId = currentUser.id();
        Page<CoachAthlete> links = linkRepository.searchActiveByCoachId(coachId, likePattern(query), groupId,
                excludeGroupId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        if (links.isEmpty()) {
            return links.map(link -> mapper.toAthleteResponse(link, List.of()));
        }
        // Seconde requête groupée (IN) pour les groupes de toute la page : pas de N+1.
        List<UUID> athleteIds = links.getContent().stream().map(link -> link.getAthlete().getId()).toList();
        Map<UUID, List<GroupRef>> groups = groupDirectory.groupsByAthlete(coachId, athleteIds);
        return links.map(link -> mapper.toAthleteResponse(link,
                groups.getOrDefault(link.getAthlete().getId(), List.of())));
    }

    /**
     * Met fin au suivi (RM5) : le lien est clôturé, pas supprimé (D5), et l'athlète est retiré des
     * groupes de ce coach par l'écouteur de {@link AthleteUnfollowedEvent}, dans la même transaction.
     * Son compte et ses liens avec d'autres coachs ne sont pas touchés.
     */
    @Transactional
    public void remove(UUID athleteId) {
        accessGuard.assertCoachOfAthlete(athleteId);
        UUID coachId = currentUser.id();
        CoachAthlete link = linkRepository.findByCoachIdAndAthleteIdAndEndedAtIsNull(coachId, athleteId)
                .orElseThrow(AthleteErrors::athleteNotFound);
        link.setEndedAt(clock.instant());
        linkRepository.flush();
        events.publishEvent(new AthleteUnfollowedEvent(coachId, athleteId));
    }

    /** {@code %q%} échappé, ou {@code null} si la recherche est vide. */
    static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        StringBuilder pattern = new StringBuilder("%");
        for (char c : query.strip().toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }
}
