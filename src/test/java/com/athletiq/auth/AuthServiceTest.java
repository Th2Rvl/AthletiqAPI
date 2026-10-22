package com.athletiq.auth;

import static com.athletiq.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.athletiq.auth.dto.LoginRequest;
import com.athletiq.auth.dto.LoginResponse;
import com.athletiq.common.exception.InvalidCredentialsException;
import com.athletiq.common.security.JwtTokenService;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserMapperImpl;
import com.athletiq.user.UserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DUMMY_HASH = "$2a$10$dummy";

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthService(userService, passwordEncoder, jwtTokenService, new UserMapperImpl());
    }

    @Test
    void returnsABearerTokenAndTheUserWithoutSensitiveData() {
        User admin = user("admin@athletiq.fr", Role.ADMIN);
        when(userService.findByEmail("admin@athletiq.fr")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("mot-de-passe-solide", admin.getPasswordHash())).thenReturn(true);
        when(jwtTokenService.createAccessToken(admin.getId(), Role.ADMIN)).thenReturn("jwt");
        when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(900L);

        LoginResponse response = authService.login(new LoginRequest(" Admin@Athletiq.FR ", "mot-de-passe-solide"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().id()).isEqualTo(admin.getId());
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void refusesAWrongPassword() {
        User admin = user("admin@athletiq.fr", Role.ADMIN);
        when(userService.findByEmail("admin@athletiq.fr")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("faux", admin.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@athletiq.fr", "faux")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtTokenService, never()).createAccessToken(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesAnUnknownEmailAfterAFakePasswordCheck() {
        when(userService.findByEmail("inconnu@club.fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("inconnu@club.fr", "peu-importe")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches(eq("peu-importe"), eq(DUMMY_HASH));
    }
}
