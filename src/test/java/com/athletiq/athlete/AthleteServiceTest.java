package com.athletiq.athlete;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.dto.AthleteResponse;
import com.athletiq.athlete.dto.GroupRef;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.group.GroupErrors;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AthleteServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    @Mock
    private CoachAthleteRepository linkRepository;
    @Mock
    private AthleteGroupDirectory groupDirectory;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private ApplicationEventPublisher events;

    private AthleteService service;
    private User coach;

    @BeforeEach
    void setUp() {
        service = new AthleteService(linkRepository, groupDirectory, new AthleteMapperImpl(), accessGuard, currentUser,
                events, Clock.fixed(NOW, ZoneOffset.UTC));
        coach = user("coach@club.fr", Role.COACH);
    }

    private CoachAthlete link(User athlete) {
        return withId(CoachAthlete.builder().coach(coach).athlete(athlete)
                .joinedAt(Instant.parse("2026-09-01T08:00:00Z")).build(), UUID.randomUUID());
    }

    // ---------------------------------------------------------------- liste

    @Test
    void listsFollowedAthletesWithTheirGroupsFromOneGroupedQuery() {
        when(currentUser.id()).thenReturn(coach.getId());
        User lucas = user("lucas.girard@athlete.fr", Role.ATHLETE);
        User emma = user("emma.lefevre@athlete.fr", Role.ATHLETE);
        GroupRef sprint = new GroupRef(UUID.randomUUID(), "Sprint");
        when(linkRepository.searchActiveByCoachId(eq(coach.getId()), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(link(lucas), link(emma)), PageRequest.of(0, 20), 2));
        when(groupDirectory.groupsByAthlete(coach.getId(), List.of(lucas.getId(), emma.getId())))
                .thenReturn(Map.of(lucas.getId(), List.of(sprint)));

        Page<AthleteResponse> page = service.list(null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(2);
        AthleteResponse first = page.getContent().get(0);
        assertThat(first.id()).isEqualTo(lucas.getId());
        assertThat(first.email()).isEqualTo("lucas.girard@athlete.fr");
        assertThat(first.joinedAt()).isEqualTo(Instant.parse("2026-09-01T08:00:00Z"));
        assertThat(first.groups()).containsExactly(sprint);
        assertThat(page.getContent().get(1).groups()).isEmpty();
        verify(accessGuard).assertCoach();
    }

    @Test
    void emptyPageDoesNotQueryGroups() {
        when(currentUser.id()).thenReturn(coach.getId());
        when(linkRepository.searchActiveByCoachId(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        assertThat(service.list("  ", null, null, PageRequest.of(0, 20))).isEmpty();
        verifyNoInteractions(groupDirectory);
    }

    @Test
    void escapesLikeWildcardsInTheSearch() {
        assertThat(AthleteService.likePattern(" mar ")).isEqualTo("%mar%");
        assertThat(AthleteService.likePattern("50%_a!b\\")).isEqualTo("%50!%!_a!!b\\%");
        assertThat(AthleteService.likePattern("")).isNull();
        assertThat(AthleteService.likePattern(null)).isNull();
    }

    @Test
    void filtersOnAGroupOfTheCoachAfterCheckingItsOwnership() {
        when(currentUser.id()).thenReturn(coach.getId());
        UUID groupId = UUID.randomUUID();
        when(linkRepository.searchActiveByCoachId(eq(coach.getId()), eq("%mar%"), eq(groupId), isNull(),
                any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.list("mar", groupId, null, PageRequest.of(0, 20));

        verify(accessGuard).assertGroupOwner(groupId);
    }

    @Test
    void refusesTheGroupOfAnotherCoachWith404() {
        UUID groupId = UUID.randomUUID();
        doThrow(GroupErrors.groupNotFound()).when(accessGuard).assertGroupOwner(groupId);

        Throwable thrown = catchThrowable(() -> service.list(null, null, groupId, PageRequest.of(0, 20)));

        assertThat(thrown).isInstanceOf(ResourceNotFoundException.class);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(GroupErrors.GROUP_NOT_FOUND);
        verifyNoInteractions(linkRepository);
    }

    @Test
    void refusesGroupIdAndExcludeGroupIdTogether() {
        Throwable thrown = catchThrowable(() -> service.list(null, UUID.randomUUID(), UUID.randomUUID(),
                PageRequest.of(0, 20)));

        assertThat(thrown).isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(linkRepository);
    }

    // ---------------------------------------------------------------- retrait

    @Test
    void removingEndsTheLinkAndAnnouncesItForTheGroups() {
        when(currentUser.id()).thenReturn(coach.getId());
        User lucas = user("lucas.girard@athlete.fr", Role.ATHLETE);
        CoachAthlete link = link(lucas);
        when(linkRepository.findByCoachIdAndAthleteIdAndEndedAtIsNull(coach.getId(), lucas.getId()))
                .thenReturn(Optional.of(link));

        service.remove(lucas.getId());

        assertThat(link.getEndedAt()).isEqualTo(NOW);
        verify(accessGuard).assertCoachOfAthlete(lucas.getId());
        verify(events).publishEvent(new AthleteUnfollowedEvent(coach.getId(), lucas.getId()));
        verify(linkRepository, never()).delete(any());
    }

    @Test
    void removingAnAthleteOfAnotherCoachAnswers404AndChangesNothing() {
        UUID athleteId = UUID.randomUUID();
        doThrow(AthleteErrors.athleteNotFound()).when(accessGuard).assertCoachOfAthlete(athleteId);

        Throwable thrown = catchThrowable(() -> service.remove(athleteId));

        assertThat(((DomainException) thrown).getCode()).isEqualTo(AthleteErrors.ATHLETE_NOT_FOUND);
        verifyNoInteractions(linkRepository, events);
    }

    @Test
    void linkServiceChecksAWholeBatchWithOneCount() {
        CoachInvitationRepository invitations = org.mockito.Mockito.mock(CoachInvitationRepository.class);
        AthleteLinkService links = new AthleteLinkService(linkRepository, invitations, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(linkRepository.countActiveByCoachIdAndAthleteIds(eq(coach.getId()), anyList())).thenReturn(1L);

        assertThat(links.followsAll(coach.getId(), List.of(a, b, a))).isFalse();
        assertThat(links.followsAll(coach.getId(), List.of())).isTrue();
    }
}
