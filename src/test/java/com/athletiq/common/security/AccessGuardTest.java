package com.athletiq.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.AthleteLinkService;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.group.GroupOwnershipService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AccessGuardTest {

    private final CurrentUser currentUser = new CurrentUser();
    private final AthleteLinkService athleteLinkService = mock(AthleteLinkService.class);
    private final GroupOwnershipService groupOwnershipService = mock(GroupOwnershipService.class);
    private final AccessGuard accessGuard = new AccessGuard(currentUser, athleteLinkService, groupOwnershipService);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static UUID authenticateAs(String role) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(id.toString()).claim("roles", List.of(role)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        return id;
    }

    @Test
    void letsAnAdminThrough() {
        UUID id = authenticateAs("ADMIN");

        assertThatCode(accessGuard::assertAdmin).doesNotThrowAnyException();
        assertThat(currentUser.id()).isEqualTo(id);
    }

    @Test
    void refusesACoach() {
        authenticateAs("COACH");

        assertThatThrownBy(accessGuard::assertAdmin)
                .isInstanceOf(ForbiddenActionException.class)
                .extracting("code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    void refusesAnAnonymousCaller() {
        assertThatThrownBy(accessGuard::assertAdmin).isInstanceOf(ForbiddenActionException.class);
    }

    // ---------------------------------------------------------------- rôles coach et athlète

    @Test
    void assertCoachAcceptsOnlyACoach() {
        authenticateAs("COACH");
        assertThatCode(accessGuard::assertCoach).doesNotThrowAnyException();

        authenticateAs("ADMIN");
        assertThatThrownBy(accessGuard::assertCoach).isInstanceOf(ForbiddenActionException.class);
        authenticateAs("ATHLETE");
        assertThatThrownBy(accessGuard::assertCoach).isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void assertAthleteAcceptsOnlyAnAthlete() {
        authenticateAs("ATHLETE");
        assertThatCode(accessGuard::assertAthlete).doesNotThrowAnyException();

        authenticateAs("COACH");
        assertThatThrownBy(accessGuard::assertAthlete).isInstanceOf(ForbiddenActionException.class);
    }

    // ---------------------------------------------------------------- propriété : 404, jamais 403

    @Test
    void assertCoachOfAthleteAcceptsAFollowedAthlete() {
        UUID coachId = authenticateAs("COACH");
        UUID athleteId = UUID.randomUUID();
        when(athleteLinkService.isFollowedBy(coachId, athleteId)).thenReturn(true);

        assertThatCode(() -> accessGuard.assertCoachOfAthlete(athleteId)).doesNotThrowAnyException();
    }

    @Test
    void assertCoachOfAthleteAnswers404ForAnAthleteOfSomebodyElse() {
        authenticateAs("COACH");

        assertThatThrownBy(() -> accessGuard.assertCoachOfAthlete(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo("ATHLETE_NOT_FOUND");
    }

    @Test
    void assertCoachOfAthletesRefusesTheWholeBatch() {
        UUID coachId = authenticateAs("COACH");
        Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());
        when(athleteLinkService.followsAll(coachId, ids)).thenReturn(false);

        assertThatThrownBy(() -> accessGuard.assertCoachOfAthletes(ids))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo("ATHLETE_NOT_FOUND");
    }

    @Test
    void assertGroupOwnerAnswers404ForTheGroupOfAnotherCoach() {
        UUID coachId = authenticateAs("COACH");
        UUID mine = UUID.randomUUID();
        when(groupOwnershipService.isOwnedBy(mine, coachId)).thenReturn(true);

        assertThatCode(() -> accessGuard.assertGroupOwner(mine)).doesNotThrowAnyException();
        assertThatThrownBy(() -> accessGuard.assertGroupOwner(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo("GROUP_NOT_FOUND");
    }

    @Test
    void assertInvitationOwnerAnswers404WhenNotPendingOrNotMine() {
        UUID coachId = authenticateAs("COACH");
        UUID mine = UUID.randomUUID();
        when(athleteLinkService.isPendingInvitationOf(coachId, mine)).thenReturn(true);

        assertThatCode(() -> accessGuard.assertInvitationOwner(mine)).doesNotThrowAnyException();
        assertThatThrownBy(() -> accessGuard.assertInvitationOwner(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo("INVITATION_NOT_FOUND");
    }

    @Test
    void ownershipChecksRefuseAnAthleteWith403BeforeAnyLookup() {
        authenticateAs("ATHLETE");

        assertThatThrownBy(() -> accessGuard.assertGroupOwner(UUID.randomUUID()))
                .isInstanceOf(ForbiddenActionException.class);
        assertThatThrownBy(() -> accessGuard.assertCoachOfAthlete(UUID.randomUUID()))
                .isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(athleteLinkService, groupOwnershipService);
    }
}
