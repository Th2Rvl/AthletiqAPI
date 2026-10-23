package com.athletiq.group;

import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.group.dto.GroupMemberRef;
import com.athletiq.group.dto.GroupMemberResponse;
import com.athletiq.group.dto.GroupResponse;
import com.athletiq.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composition des groupes du coach connecté (US14). */
@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final AthleteGroupRepository groupRepository;
    private final AthleteGroupMemberRepository memberRepository;
    private final GroupMapper mapper;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final Clock clock;

    /** Membres du groupe, triés par nom puis prénom. */
    @Transactional(readOnly = true)
    public Page<GroupMemberResponse> list(UUID groupId, Pageable pageable) {
        accessGuard.assertGroupOwner(groupId);
        return memberRepository.findPageByGroupId(groupId,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(mapper::toMemberResponse);
    }

    /**
     * Tous les membres actuels du groupe, triés par nom puis prénom, non paginé : la planification (#6)
     * crée une séance par membre présent au moment de planifier (Q3).
     */
    @Transactional(readOnly = true)
    public List<GroupMemberRef> listAllMembers(UUID groupId) {
        accessGuard.assertGroupOwner(groupId);
        return memberRepository.findAllRefsByGroupId(groupId);
    }

    /**
     * Ajoute un lot d'athlètes (RM8, D8). Atomique : si un seul athlète n'est pas suivi par le coach,
     * rien n'est ajouté (404 {@code ATHLETE_NOT_FOUND}). Idempotent : un athlète déjà membre, ou
     * présent deux fois dans la liste, est ignoré (P9). Le groupe est verrouillé pendant l'ajout pour
     * que deux ajouts simultanés ne se gênent pas.
     */
    @Transactional
    public GroupResponse add(UUID groupId, List<UUID> athleteIds) {
        accessGuard.assertGroupOwner(groupId);
        Set<UUID> requested = new LinkedHashSet<>(athleteIds);
        accessGuard.assertCoachOfAthletes(requested);

        AthleteGroup group = groupRepository.findByIdAndCoachIdForUpdate(groupId, currentUser.id())
                .orElseThrow(GroupErrors::groupNotFound);
        Set<UUID> existing = new HashSet<>(memberRepository.findAthleteIdsByGroupIdAndAthleteIdIn(groupId, requested));
        Instant now = clock.instant();
        List<AthleteGroupMember> added = requested.stream()
                .filter(athleteId -> !existing.contains(athleteId))
                .map(athleteId -> AthleteGroupMember.builder()
                        .group(group)
                        .athlete(userService.getReference(athleteId))
                        .addedAt(now)
                        .build())
                .toList();
        memberRepository.saveAllAndFlush(added);
        return mapper.toResponse(group, memberRepository.countByGroupId(groupId));
    }

    /** Retire l'athlète du groupe ; il reste suivi par le coach. */
    @Transactional
    public void remove(UUID groupId, UUID athleteId) {
        accessGuard.assertGroupOwner(groupId);
        if (memberRepository.deleteByGroupIdAndAthleteId(groupId, athleteId) == 0) {
            throw GroupErrors.memberNotFound();
        }
    }
}
