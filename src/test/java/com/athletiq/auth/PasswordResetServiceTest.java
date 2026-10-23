package com.athletiq.auth;

import static com.athletiq.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.athletiq.auth.dto.ForgotPasswordRequest;
import com.athletiq.auth.dto.ForgotPasswordResponse;
import com.athletiq.auth.dto.ResetPasswordRequest;
import com.athletiq.common.exception.InvalidFieldException;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private UserService userService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, userService,
                new ResetTokenProperties(true, Duration.ofHours(1)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsATokenForAKnownAccount() {
        User coach = user("coach@club.fr", Role.COACH);
        when(userService.findByEmail("coach@club.fr")).thenReturn(Optional.of(coach));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(coach.getId())).thenReturn(List.of());

        ForgotPasswordResponse response = service.requestReset(new ForgotPasswordRequest(" Coach@Club.FR "));

        assertThat(response.accepted()).isTrue();
        assertThat(response.message()).isEqualTo(ForgotPasswordResponse.MESSAGE);
        assertThat(response.resetToken()).isNotBlank();

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(coach);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(saved.getValue().getTokenHash()).hasSize(64).doesNotContain(response.resetToken());
    }

    @Test
    void answersTheSameWayForAnUnknownEmail() {
        when(userService.findByEmail("inconnu@club.fr")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = service.requestReset(new ForgotPasswordRequest("inconnu@club.fr"));

        assertThat(response.accepted()).isTrue();
        assertThat(response.message()).isEqualTo(ForgotPasswordResponse.MESSAGE);
        assertThat(response.resetToken()).isNull();
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void revokesThePreviousTokensOfTheAccount() {
        User coach = user("coach@club.fr", Role.COACH);
        PasswordResetToken previous = PasswordResetToken.builder()
                .user(coach)
                .tokenHash("a".repeat(64))
                .expiresAt(NOW.plus(Duration.ofHours(1)))
                .build();
        when(userService.findByEmail("coach@club.fr")).thenReturn(Optional.of(coach));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(coach.getId())).thenReturn(List.of(previous));

        service.requestReset(new ForgotPasswordRequest("coach@club.fr"));

        assertThat(previous.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void changesThePasswordWithAValidToken() {
        User coach = user("coach@club.fr", Role.COACH);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(coach)
                .tokenHash("a".repeat(64))
                .expiresAt(NOW.plus(Duration.ofMinutes(30)))
                .build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        service.resetPassword(new ResetPasswordRequest("jeton-valide", "nouveaumotdepasse1", "nouveaumotdepasse1"));

        verify(userService).updatePassword(coach.getId(), "nouveaumotdepasse1");
        assertThat(token.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesAnExpiredToken() {
        User coach = user("coach@club.fr", Role.COACH);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(coach)
                .tokenHash("a".repeat(64))
                .expiresAt(NOW.minusSeconds(1))
                .build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("jeton", "nouveaumotdepasse1", "nouveaumotdepasse1")))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("plus valable");
        verify(userService, never()).updatePassword(any(), any());
    }

    @Test
    void refusesATokenAlreadyUsed() {
        User coach = user("coach@club.fr", Role.COACH);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(coach)
                .tokenHash("a".repeat(64))
                .expiresAt(NOW.plus(Duration.ofMinutes(30)))
                .usedAt(NOW.minusSeconds(10))
                .build();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("jeton", "nouveaumotdepasse1", "nouveaumotdepasse1")))
                .isInstanceOf(InvalidFieldException.class);
        verify(userService, never()).updatePassword(any(), any());
    }

    @Test
    void refusesAnUnknownToken() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("jeton", "nouveaumotdepasse1", "nouveaumotdepasse1")))
                .isInstanceOf(InvalidFieldException.class);
        verify(userService, never()).updatePassword(any(), any());
    }

    @Test
    void refusesDifferentPasswords() {
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("jeton", "nouveaumotdepasse1", "autremotdepasse2")))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("ne correspondent pas");
        verify(tokenRepository, never()).findByTokenHash(any());
    }
}
