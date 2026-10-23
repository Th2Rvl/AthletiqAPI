package com.athletiq.athlete;

import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.athlete.dto.CreateInvitationRequest;
import com.athletiq.athlete.dto.InvitationResponse;
import com.athletiq.athlete.dto.RedeemInvitationRequest;
import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.InvalidFieldException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock
    private CoachInvitationRepository invitationRepository;
    @Mock
    private CoachAthleteRepository linkRepository;
    @Mock
    private UserService userService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private CurrentUser currentUser;

    private final InvitationCodeCipher cipher =
            new InvitationCodeCipher(new InvitationProperties(KEY, KEY, Duration.ofDays(7)));
    private InvitationService service;
    private User coach;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitationRepository, linkRepository, cipher,
                new InvitationProperties(KEY, KEY, Duration.ofDays(7)), new AthleteMapperImpl(), userService,
                accessGuard, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
        coach = user("coach@club.fr", Role.COACH);
        athlete = user("lucas.girard@athlete.fr", Role.ATHLETE);
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    private CoachInvitation invitation(String code, Consumer<CoachInvitation> state) {
        CoachInvitation invitation = withId(CoachInvitation.builder()
                .coach(coach)
                .label("Lucas Girard")
                .codeEncrypted(cipher.encrypt(code, coach.getId()))
                .codeHash(cipher.hash(code))
                .expiresAt(NOW.plus(Duration.ofDays(3)))
                .build(), UUID.randomUUID());
        state.accept(invitation);
        return invitation;
    }

    // ---------------------------------------------------------------- côté coach

    @Nested
    class Create {

        @BeforeEach
        void asCoach() {
            when(currentUser.id()).thenReturn(coach.getId());
            lenient().when(userService.getReference(coach.getId())).thenReturn(coach);
            lenient().when(invitationRepository.saveAndFlush(any()))
                    .thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));
        }

        @Test
        void storesTheCodeEncryptedAndHashedNeverInClear() {
            when(invitationRepository.existsByCodeHash(anyString())).thenReturn(false);

            InvitationResponse response = service.create(new CreateInvitationRequest("  Lucas Girard "));

            ArgumentCaptor<CoachInvitation> saved = ArgumentCaptor.forClass(CoachInvitation.class);
            verify(invitationRepository).saveAndFlush(saved.capture());
            CoachInvitation invitation = saved.getValue();
            assertThat(response.code()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}");
            assertThat(response.label()).isEqualTo("Lucas Girard");
            assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
            assertThat(invitation.getCoach()).isEqualTo(coach);
            assertThat(invitation.getCodeHash()).isEqualTo(cipher.hash(response.code()));
            assertThat(cipher.decrypt(invitation.getCodeEncrypted(), coach.getId())).isEqualTo(response.code());
            assertThat(new String(invitation.getCodeEncrypted())).doesNotContain(response.code());
            verify(accessGuard).assertCoach();
        }

        @Test
        void drawsAnotherCodeWhenTheHashAlreadyExists() {
            when(invitationRepository.existsByCodeHash(anyString())).thenReturn(true, true, false);

            InvitationResponse response = service.create(new CreateInvitationRequest("Lucas"));

            assertThat(response.code()).hasSize(8);
            verify(invitationRepository, times(3)).existsByCodeHash(anyString());
        }

        @Test
        void givesUpAfterFiveCollisions() {
            when(invitationRepository.existsByCodeHash(anyString())).thenReturn(true);

            assertThatThrownBy(() -> service.create(new CreateInvitationRequest("Lucas")))
                    .isInstanceOf(IllegalStateException.class);
            verify(invitationRepository, times(InvitationService.MAX_GENERATION_ATTEMPTS)).existsByCodeHash(anyString());
            verify(invitationRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    void createRefusesANonCoach() {
        doThrow(new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé")).when(accessGuard).assertCoach();

        assertThatThrownBy(() -> service.create(new CreateInvitationRequest("Lucas")))
                .isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(invitationRepository);
    }

    @Test
    void listsPendingInvitationsWithTheirDecryptedCode() {
        when(currentUser.id()).thenReturn(coach.getId());
        CoachInvitation pending = invitation("K7QH3MPA", i -> { });
        when(invitationRepository.findPendingByCoachId(eq(coach.getId()), eq(NOW), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending), PageRequest.of(0, 20), 1));

        List<InvitationResponse> content = service.listPending(PageRequest.of(0, 20)).getContent();

        assertThat(content).singleElement().satisfies(response -> {
            assertThat(response.code()).isEqualTo("K7QH3MPA");
            assertThat(response.label()).isEqualTo("Lucas Girard");
        });
        verify(accessGuard).assertCoach();
    }

    @Test
    void revokesAPendingInvitation() {
        when(currentUser.id()).thenReturn(coach.getId());
        CoachInvitation pending = invitation("K7QH3MPA", i -> { });
        when(invitationRepository.findPendingByIdAndCoachId(pending.getId(), coach.getId(), NOW))
                .thenReturn(Optional.of(pending));

        service.revoke(pending.getId());

        assertThat(pending.getRevokedAt()).isEqualTo(NOW);
        verify(accessGuard).assertInvitationOwner(pending.getId());
    }

    @Test
    void revokeAnswers404ForTheInvitationOfAnotherCoach() {
        UUID invitationId = UUID.randomUUID();
        doThrow(AthleteErrors.invitationNotFound()).when(accessGuard).assertInvitationOwner(invitationId);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> service.revoke(invitationId));

        assertDomainError(thrown, ResourceNotFoundException.class, AthleteErrors.INVITATION_NOT_FOUND);
        verifyNoInteractions(invitationRepository);
    }

    // ---------------------------------------------------------------- côté athlète

    @Nested
    class Redeem {

        @BeforeEach
        void asAthlete() {
            lenient().when(currentUser.id()).thenReturn(athlete.getId());
            lenient().when(userService.getReference(athlete.getId())).thenReturn(athlete);
        }

        @Test
        void linksTheAthleteToTheCoachAndConsumesTheCode() {
            CoachInvitation pending = invitation("K7QH3MPA", i -> { });
            when(invitationRepository.findByCodeHash(cipher.hash("K7QH3MPA"))).thenReturn(Optional.of(pending));
            when(invitationRepository.consume(cipher.hash("K7QH3MPA"), athlete, NOW)).thenReturn(1);

            CoachLinkResponse response = service.redeem(new RedeemInvitationRequest(" k7qh-3mpa "));

            assertThat(response.coachId()).isEqualTo(coach.getId());
            assertThat(response.firstName()).isEqualTo("Claire");
            assertThat(response.joinedAt()).isEqualTo(NOW);
            ArgumentCaptor<CoachAthlete> link = ArgumentCaptor.forClass(CoachAthlete.class);
            verify(linkRepository).saveAndFlush(link.capture());
            assertThat(link.getValue().getCoach()).isEqualTo(coach);
            assertThat(link.getValue().getAthlete()).isEqualTo(athlete);
            assertThat(link.getValue().getEndedAt()).isNull();
            verify(accessGuard).assertAthlete();
        }

        @Test
        void refusesAMalformedCodeWithTheSameErrorWithoutLookingItUp() {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.redeem(new RedeemInvitationRequest("K7QH-3MP0")));

            assertDomainError(thrown, InvalidFieldException.class, AthleteErrors.INVITATION_CODE_INVALID);
            assertThat(((DomainException) thrown).getField()).isEqualTo("code");
            verifyNoInteractions(invitationRepository, linkRepository);
        }

        @Test
        void refusesAnUnknownCode() {
            when(invitationRepository.findByCodeHash(anyString())).thenReturn(Optional.empty());

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.redeem(new RedeemInvitationRequest("K7QH3MPA")));

            assertDomainError(thrown, InvalidFieldException.class, AthleteErrors.INVITATION_CODE_INVALID);
        }

        @Test
        void refusesExpiredRevokedAndConsumedCodesWithTheSameError() {
            List<Consumer<CoachInvitation>> states = List.of(
                    i -> i.setExpiresAt(NOW),
                    i -> i.setRevokedAt(NOW.minusSeconds(60)),
                    i -> i.setConsumedAt(NOW.minusSeconds(60)));
            for (Consumer<CoachInvitation> state : states) {
                CoachInvitation invitation = invitation("K7QH3MPA", state);
                when(invitationRepository.findByCodeHash(cipher.hash("K7QH3MPA"))).thenReturn(Optional.of(invitation));

                Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                        () -> service.redeem(new RedeemInvitationRequest("K7QH3MPA")));

                assertDomainError(thrown, InvalidFieldException.class, AthleteErrors.INVITATION_CODE_INVALID);
            }
            verify(invitationRepository, never()).consume(anyString(), any(), any());
            verifyNoInteractions(linkRepository);
        }

        @Test
        void refusesACodeWhoseOwnerIsNoLongerACoach() {
            coach.setRole(Role.ADMIN);
            CoachInvitation invitation = invitation("K7QH3MPA", i -> { });
            when(invitationRepository.findByCodeHash(anyString())).thenReturn(Optional.of(invitation));

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.redeem(new RedeemInvitationRequest("K7QH3MPA")));

            assertDomainError(thrown, InvalidFieldException.class, AthleteErrors.INVITATION_CODE_INVALID);
        }

        @Test
        void refusesAnAthleteAlreadyFollowedWithoutConsumingTheCode() {
            CoachInvitation pending = invitation("K7QH3MPA", i -> { });
            when(invitationRepository.findByCodeHash(anyString())).thenReturn(Optional.of(pending));
            when(linkRepository.existsByCoachIdAndAthleteIdAndEndedAtIsNull(coach.getId(), athlete.getId()))
                    .thenReturn(true);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.redeem(new RedeemInvitationRequest("K7QH3MPA")));

            assertDomainError(thrown, BusinessRuleException.class, AthleteErrors.ATHLETE_ALREADY_LINKED);
            verify(invitationRepository, never()).consume(anyString(), any(), any());
            verify(linkRepository, never()).saveAndFlush(any());
        }

        @Test
        void refusesWhenAnotherAthleteConsumedTheCodeFirst() {
            CoachInvitation pending = invitation("K7QH3MPA", i -> { });
            when(invitationRepository.findByCodeHash(anyString())).thenReturn(Optional.of(pending));
            when(invitationRepository.consume(anyString(), any(), any())).thenReturn(0);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.redeem(new RedeemInvitationRequest("K7QH3MPA")));

            assertDomainError(thrown, InvalidFieldException.class, AthleteErrors.INVITATION_CODE_INVALID);
            verify(linkRepository, never()).saveAndFlush(any());
        }

        @Test
        void translatesAConcurrentActiveLinkIntoAlreadyLinked() {
            CoachInvitation pending = invitation("K7QH3MPA", i -> { });
            when(invitationRepository.findByCodeHash(anyString())).thenReturn(Optional.of(pending));
            when(invitationRepository.consume(anyString(), any(), any())).thenReturn(1);
            when(linkRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("doublon",
                    new SQLIntegrityConstraintViolationException(
                            "Duplicate entry for key 'coach_athlete.uk_coach_athlete_active'")));

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.redeem(new RedeemInvitationRequest("K7QH3MPA")));

            assertDomainError(thrown, BusinessRuleException.class, AthleteErrors.ATHLETE_ALREADY_LINKED);
        }
    }

    @Test
    void redeemRefusesACoach() {
        doThrow(new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé")).when(accessGuard).assertAthlete();

        assertThatThrownBy(() -> service.redeem(new RedeemInvitationRequest("K7QH3MPA")))
                .isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(invitationRepository, linkRepository);
    }
}
