package com.athletiq.group;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.group.dto.GroupMemberRef;
import com.athletiq.group.dto.GroupResponse;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

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

    private GroupMemberService service;
    private User coach;
    private AthleteGroup sprint;

    @BeforeEach
    void setUp() {
        service = new GroupMemberService(groupRepository, memberRepository, new GroupMapperImpl(), userService,
                accessGuard, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
        coach = user("coach@club.fr", Role.COACH);
        sprint = withId(AthleteGroup.builder().coach(coach).name("Sprint").build(), UUID.randomUUID());
    }

    @Test
    @SuppressWarnings("unchecked")
    void addsOnlyNewMembersAndIgnoresDuplicates() {
        when(currentUser.id()).thenReturn(coach.getId());
        User lucas = user("lucas.girard@athlete.fr", Role.ATHLETE);
        User emma = user("emma.lefevre@athlete.fr", Role.ATHLETE);
        when(groupRepository.findByIdAndCoachIdForUpdate(sprint.getId(), coach.getId())).thenReturn(Optional.of(sprint));
        when(memberRepository.findAthleteIdsByGroupIdAndAthleteIdIn(any(), anyCollection()))
                .thenReturn(List.of(lucas.getId()));
        when(userService.getReference(emma.getId())).thenReturn(emma);
        when(memberRepository.countByGroupId(sprint.getId())).thenReturn(2L);

        GroupResponse response = service.add(sprint.getId(), List.of(lucas.getId(), emma.getId(), emma.getId()));

        assertThat(response.athleteCount()).isEqualTo(2);
        verify(accessGuard).assertCoachOfAthletes(Set.of(lucas.getId(), emma.getId()));
        ArgumentCaptor<List<AthleteGroupMember>> saved = ArgumentCaptor.forClass(List.class);
        verify(memberRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(member -> {
            assertThat(member.getAthlete()).isEqualTo(emma);
            assertThat(member.getGroup()).isEqualTo(sprint);
            assertThat(member.getAddedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void refusesTheWholeBatchWhenOneAthleteIsNotFollowed() {
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthletes(anyCollection());

        Throwable thrown = catchThrowable(() -> service.add(sprint.getId(), List.of(UUID.randomUUID(), UUID.randomUUID())));

        assertThat(((DomainException) thrown).getCode()).isEqualTo(AthleteErrors.ATHLETE_NOT_FOUND);
        verify(memberRepository, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(groupRepository);
    }

    @Test
    void addingToTheGroupOfAnotherCoachAnswers404() {
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(sprint.getId());

        Throwable thrown = catchThrowable(() -> service.add(sprint.getId(), List.of(UUID.randomUUID())));

        assertThat(((DomainException) thrown).getCode()).isEqualTo(GroupErrors.GROUP_NOT_FOUND);
        verifyNoInteractions(groupRepository, memberRepository);
    }

    @Test
    void removesAMember() {
        UUID athleteId = UUID.randomUUID();
        when(memberRepository.deleteByGroupIdAndAthleteId(sprint.getId(), athleteId)).thenReturn(1);

        service.remove(sprint.getId(), athleteId);

        verify(accessGuard).assertGroupOwner(sprint.getId());
    }

    @Test
    void removingANonMemberAnswers404() {
        Throwable thrown = catchThrowable(() -> service.remove(sprint.getId(), UUID.randomUUID()));

        assertThat(thrown).isInstanceOf(ResourceNotFoundException.class);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(GroupErrors.GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    void listingTheMembersOfAnotherCoachGroupAnswers404() {
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(sprint.getId());

        Throwable thrown = catchThrowable(() -> service.list(sprint.getId(), org.springframework.data.domain.PageRequest.of(0, 20)));

        assertThat(((DomainException) thrown).getCode()).isEqualTo(GroupErrors.GROUP_NOT_FOUND);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void listAllMembersChecksTheOwnerFirst() {
        List<GroupMemberRef> members = List.of(new GroupMemberRef(UUID.randomUUID(), "Lucas", "Girard"));
        when(memberRepository.findAllRefsByGroupId(sprint.getId())).thenReturn(members);

        assertThat(service.listAllMembers(sprint.getId())).isEqualTo(members);
        verify(accessGuard).assertGroupOwner(sprint.getId());
    }

    @Test
    void listAllMembersOfTheGroupOfAnotherCoachAnswers404() {
        UUID foreign = UUID.randomUUID();
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(foreign);

        Throwable thrown = catchThrowable(() -> service.listAllMembers(foreign));

        assertThat(thrown).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(memberRepository);
    }
}
