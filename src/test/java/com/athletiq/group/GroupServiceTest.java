package com.athletiq.group;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.group.dto.CreateGroupRequest;
import com.athletiq.group.dto.GroupResponse;
import com.athletiq.group.dto.RenameGroupRequest;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private AthleteGroupRepository groupRepository;
    @Mock
    private AthleteGroupMemberRepository memberRepository;
    @Mock
    private UserService userService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private GroupService service;
    private User coach;

    @BeforeEach
    void setUp() {
        service = new GroupService(groupRepository, memberRepository, new GroupMapperImpl(), userService, accessGuard,
                currentUser);
        coach = user("coach@club.fr", Role.COACH);
    }

    private AthleteGroup group(String name) {
        return withId(AthleteGroup.builder().coach(coach).name(name).build(), UUID.randomUUID());
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    @Test
    void listsTheGroupsOfTheCoachWithTheirCount() {
        when(currentUser.id()).thenReturn(coach.getId());
        UUID id = UUID.randomUUID();
        when(groupRepository.findSummariesByCoachId(eq(coach.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new GroupSummary(id, "Sprint", 3, Instant.EPOCH))));

        List<GroupResponse> content = service.list(PageRequest.of(0, 20)).getContent();

        assertThat(content).containsExactly(new GroupResponse(id, "Sprint", 3, Instant.EPOCH));
        verify(accessGuard).assertCoach();
    }

    @Test
    void createsAGroupWithNoAthlete() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        when(groupRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        GroupResponse created = service.create(new CreateGroupRequest("  Sprint  "));

        assertThat(created.name()).isEqualTo("Sprint");
        assertThat(created.athleteCount()).isZero();
        verify(groupRepository).existsByCoachIdAndName(coach.getId(), "Sprint");
    }

    @Test
    void refusesADuplicateNameOfTheSameCoach() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(groupRepository.existsByCoachIdAndName(coach.getId(), "sprint")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.create(new CreateGroupRequest("sprint")));

        assertDomainError(thrown, BusinessRuleException.class, GroupErrors.GROUP_NAME_ALREADY_EXISTS);
        assertThat(((DomainException) thrown).getField()).isEqualTo("name");
        verify(groupRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesTheUniqueConstraintIntoTheSameError() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(userService.getReference(coach.getId())).thenReturn(coach);
        when(groupRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("doublon",
                new SQLIntegrityConstraintViolationException("Duplicate entry for key 'athlete_group.uk_athlete_group_name'")));

        Throwable thrown = catchThrowable(() -> service.create(new CreateGroupRequest("Sprínt")));

        assertDomainError(thrown, BusinessRuleException.class, GroupErrors.GROUP_NAME_ALREADY_EXISTS);
    }

    @Test
    void renamingWithItsOwnNameIsAccepted() {
        when(currentUser.id()).thenReturn(coach.getId());
        AthleteGroup sprint = group("Sprint");
        when(groupRepository.findByIdAndCoachId(sprint.getId(), coach.getId())).thenReturn(Optional.of(sprint));
        when(groupRepository.existsByCoachIdAndNameAndIdNot(coach.getId(), "SPRINT", sprint.getId())).thenReturn(false);
        when(groupRepository.saveAndFlush(sprint)).thenReturn(sprint);
        when(memberRepository.countByGroupId(sprint.getId())).thenReturn(4L);

        GroupResponse renamed = service.rename(sprint.getId(), new RenameGroupRequest(" SPRINT "));

        assertThat(renamed.name()).isEqualTo("SPRINT");
        assertThat(renamed.athleteCount()).isEqualTo(4);
        verify(accessGuard).assertGroupOwner(sprint.getId());
    }

    @Test
    void renamingToTheNameOfAnotherGroupIsRefused() {
        when(currentUser.id()).thenReturn(coach.getId());
        AthleteGroup sprint = group("Sprint");
        when(groupRepository.findByIdAndCoachId(sprint.getId(), coach.getId())).thenReturn(Optional.of(sprint));
        when(groupRepository.existsByCoachIdAndNameAndIdNot(coach.getId(), "demi-fond", sprint.getId())).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.rename(sprint.getId(), new RenameGroupRequest("demi-fond")));

        assertDomainError(thrown, BusinessRuleException.class, GroupErrors.GROUP_NAME_ALREADY_EXISTS);
        assertThat(sprint.getName()).isEqualTo("Sprint");
    }

    @Test
    void theGroupOfAnotherCoachAnswers404ForEveryAction() {
        UUID groupId = UUID.randomUUID();
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(groupId);

        assertDomainError(catchThrowable(() -> service.get(groupId)), ResourceNotFoundException.class,
                GroupErrors.GROUP_NOT_FOUND);
        assertDomainError(catchThrowable(() -> service.rename(groupId, new RenameGroupRequest("X"))),
                ResourceNotFoundException.class, GroupErrors.GROUP_NOT_FOUND);
        assertDomainError(catchThrowable(() -> service.delete(groupId)), ResourceNotFoundException.class,
                GroupErrors.GROUP_NOT_FOUND);
        verifyNoInteractions(groupRepository, memberRepository);
    }

    @Test
    void deletesTheGroupOnly() {
        when(currentUser.id()).thenReturn(coach.getId());
        AthleteGroup sprint = group("Sprint");
        when(groupRepository.findByIdAndCoachId(sprint.getId(), coach.getId())).thenReturn(Optional.of(sprint));

        service.delete(sprint.getId());

        verify(groupRepository).delete(sprint);
        verifyNoInteractions(userService);
    }
}
