package com.athletiq.athlete;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MyCoachesServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Mock
    private CoachAthleteRepository linkRepository;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private ApplicationEventPublisher events;

    private MyCoachesService service;
    private User coach;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new MyCoachesService(linkRepository, new AthleteMapperImpl(), accessGuard, currentUser, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        coach = user("coach@club.fr", Role.COACH);
        athlete = user("athlete@mail.fr", Role.ATHLETE);
    }

    @Test
    void listsTheCoachesFollowingTheAthlete() {
        when(currentUser.id()).thenReturn(athlete.getId());
        CoachAthlete link = CoachAthlete.builder().coach(coach).athlete(athlete).joinedAt(NOW).build();
        when(linkRepository.findActiveByAthleteId(athlete.getId())).thenReturn(List.of(link));

        List<CoachLinkResponse> coaches = service.list();

        assertThat(coaches).singleElement().satisfies(response -> {
            assertThat(response.coachId()).isEqualTo(coach.getId());
            assertThat(response.joinedAt()).isEqualTo(NOW);
        });
        verify(accessGuard).assertAthlete();
    }

    @Test
    void listsNothingForAnAthleteWithoutCoach() {
        when(currentUser.id()).thenReturn(athlete.getId());
        when(linkRepository.findActiveByAthleteId(athlete.getId())).thenReturn(List.of());

        assertThat(service.list()).isEmpty();
    }

    @Test
    void leavingClosesTheLinkAndPublishesTheUnfollowEvent() {
        when(currentUser.id()).thenReturn(athlete.getId());
        CoachAthlete link = withId(CoachAthlete.builder().coach(coach).athlete(athlete).joinedAt(NOW.minusSeconds(60)).build(),
                UUID.randomUUID());
        when(linkRepository.findByCoachIdAndAthleteIdAndEndedAtIsNull(coach.getId(), athlete.getId()))
                .thenReturn(Optional.of(link));

        service.leave(coach.getId());

        assertThat(link.getEndedAt()).isEqualTo(NOW);
        verify(linkRepository).flush();
        verify(events).publishEvent(new AthleteUnfollowedEvent(coach.getId(), athlete.getId()));
    }

    @Test
    void leavingACoachThatDoesNotFollowTheAthleteIs404() {
        when(currentUser.id()).thenReturn(athlete.getId());
        when(linkRepository.findByCoachIdAndAthleteIdAndEndedAtIsNull(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ne suivez plus");
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void refusesANonAthlete() {
        org.mockito.Mockito.doThrow(new ForbiddenActionException("ACCESS_DENIED", "Accès refusé"))
                .when(accessGuard).assertAthlete();

        assertThatThrownBy(() -> service.list()).isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> service.leave(UUID.randomUUID())).isInstanceOf(ForbiddenActionException.class);
    }
}
