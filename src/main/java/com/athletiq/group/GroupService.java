package com.athletiq.group;

import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.group.dto.CreateGroupRequest;
import com.athletiq.group.dto.GroupResponse;
import com.athletiq.group.dto.RenameGroupRequest;
import com.athletiq.user.UserService;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groupes du coach connecté (US13). Chaque méthode commence par l'{@code AccessGuard} : rôle
 * {@code COACH}, puis propriété du groupe (404 sinon).
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    static final String UK_ATHLETE_GROUP_NAME = "uk_athlete_group_name";

    private final AthleteGroupRepository groupRepository;
    private final AthleteGroupMemberRepository memberRepository;
    private final GroupMapper mapper;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;

    /** Groupes du coach, triés par nom (D6), avec leur nombre d'athlètes. */
    @Transactional(readOnly = true)
    public Page<GroupResponse> list(Pageable pageable) {
        accessGuard.assertCoach();
        return groupRepository.findSummariesByCoachId(currentUser.id(),
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public GroupResponse get(UUID groupId) {
        accessGuard.assertGroupOwner(groupId);
        return toResponse(findOwnGroup(groupId));
    }

    /** Le nom est unique par coach, sans tenir compte de la casse ni des accents (RM7). */
    @Transactional
    public GroupResponse create(CreateGroupRequest request) {
        accessGuard.assertCoach();
        UUID coachId = currentUser.id();
        if (groupRepository.existsByCoachIdAndName(coachId, request.name())) {
            throw GroupErrors.nameAlreadyExists();
        }
        AthleteGroup group = AthleteGroup.builder()
                .coach(userService.getReference(coachId))
                .name(request.name())
                .build();
        return mapper.toResponse(saveAndFlush(() -> groupRepository.saveAndFlush(group)), 0L);
    }

    /** Renommer un groupe avec son propre nom (casse ou accents différents) est accepté. */
    @Transactional
    public GroupResponse rename(UUID groupId, RenameGroupRequest request) {
        accessGuard.assertGroupOwner(groupId);
        AthleteGroup group = findOwnGroup(groupId);
        if (groupRepository.existsByCoachIdAndNameAndIdNot(currentUser.id(), request.name(), groupId)) {
            throw GroupErrors.nameAlreadyExists();
        }
        group.setName(request.name());
        return toResponse(saveAndFlush(() -> groupRepository.saveAndFlush(group)));
    }

    /**
     * Suppression physique (RM9). Les appartenances partent avec le groupe (FK {@code ON DELETE
     * CASCADE}) ; les athlètes et leurs liens avec le coach ne sont pas touchés.
     */
    @Transactional
    public void delete(UUID groupId) {
        accessGuard.assertGroupOwner(groupId);
        groupRepository.delete(findOwnGroup(groupId));
    }

    // ---------------------------------------------------------------- interne

    private GroupResponse toResponse(AthleteGroup group) {
        return mapper.toResponse(group, memberRepository.countByGroupId(group.getId()));
    }

    /** Le filtre sur le coach reste dans la requête, même après l'{@code AccessGuard}. */
    private AthleteGroup findOwnGroup(UUID groupId) {
        return groupRepository.findByIdAndCoachId(groupId, currentUser.id())
                .orElseThrow(GroupErrors::groupNotFound);
    }

    /** La contrainte unique protège en dernier recours (deux créations simultanées) : même erreur métier. */
    private static <T> T saveAndFlush(Supplier<T> save) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(UK_ATHLETE_GROUP_NAME)) {
                throw GroupErrors.nameAlreadyExists();
            }
            throw ex;
        }
    }
}
