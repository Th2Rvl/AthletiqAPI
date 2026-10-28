package com.athletiq.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.auth.dto.ForgotPasswordRequest;
import com.athletiq.auth.dto.ForgotPasswordResponse;
import com.athletiq.auth.dto.LoginRequest;
import com.athletiq.auth.dto.LoginResponse;
import com.athletiq.auth.dto.RegisterAthleteRequest;
import com.athletiq.auth.dto.RegisterCoachRequest;
import com.athletiq.common.exception.InvalidCredentialsException;
import com.athletiq.common.exception.InvalidFieldException;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import com.athletiq.user.dto.UserResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String REGISTER_ATHLETE = "/api/v1/auth/register/athlete";
    private static final String FORGOT = "/api/v1/auth/password/forgot";
    private static final String RESET = "/api/v1/auth/password/reset";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    void logsInWithoutToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class))).thenReturn(LoginResponse.bearer("jwt", 900,
                new UserResponse(id, "admin@athletiq.fr", "Admin", "Athletiq", Role.ADMIN)));

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@athletiq.fr\",\"password\":\"mot-de-passe-solide\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void returns401InvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@athletiq.fr\",\"password\":\"faux\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("E-mail ou mot de passe incorrect"));
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(authService);
    }

    // ---------------------------------------------------------------- inscription (US06)

    @Test
    void registersACoachWithoutTokenAndConnectsIt() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.register(any(RegisterCoachRequest.class))).thenReturn(LoginResponse.bearer("jwt", 900,
                new UserResponse(id, "claire.martin@club.fr", "Claire", "Martin", Role.COACH)));

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"claire.martin@club.fr\",\"password\":\"motdepasse123\","
                                + "\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.user.role").value("COACH"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void registersAnAthleteWithoutTokenAndConnectsIt() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.registerAthlete(any(RegisterAthleteRequest.class))).thenReturn(LoginResponse.bearer("jwt", 900,
                new UserResponse(id, "lea.durand@mail.fr", "Léa", "Durand", Role.ATHLETE)));

        mockMvc.perform(post(REGISTER_ATHLETE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lea.durand@mail.fr\",\"password\":\"motdepasse123\","
                                + "\"firstName\":\"Léa\",\"lastName\":\"Durand\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.user.role").value("ATHLETE"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void validatesTheAthleteRegistration() throws Exception {
        mockMvc.perform(post(REGISTER_ATHLETE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\",\"password\":\"court\",\"firstName\":\"\",\"lastName\":\"Durand\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(authService);
    }

    @Test
    void refusesARegistrationWithAnAlreadyUsedEmail() throws Exception {
        when(authService.register(any(RegisterCoachRequest.class)))
                .thenThrow(new com.athletiq.common.exception.BusinessRuleException("EMAIL_ALREADY_EXISTS",
                        "Un compte existe déjà avec cet e-mail", "email"));

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"claire.martin@club.fr\",\"password\":\"motdepasse123\","
                                + "\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void validatesTheRegistration() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\",\"password\":\"court\",\"firstName\":\"\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
        verifyNoInteractions(authService);
    }

    // ---------------------------------------------------------------- mot de passe oublié (US08)

    @Test
    void acceptsAForgottenPasswordRequest() throws Exception {
        when(passwordResetService.requestReset(any(ForgotPasswordRequest.class)))
                .thenReturn(ForgotPasswordResponse.accepted("jeton"));

        mockMvc.perform(post(FORGOT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"coach@club.fr\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.resetToken").value("jeton"));
    }

    @Test
    void validatesTheForgottenPasswordRequest() throws Exception {
        mockMvc.perform(post(FORGOT).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetsThePassword() throws Exception {
        mockMvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"jeton\",\"password\":\"nouveaumotdepasse1\","
                                + "\"passwordConfirmation\":\"nouveaumotdepasse1\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void refusesAnInvalidResetToken() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidFieldException("RESET_TOKEN_INVALID",
                "Ce lien de réinitialisation n'est plus valable", "token"))
                .when(passwordResetService).resetPassword(any());

        mockMvc.perform(post(RESET).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"jeton\",\"password\":\"nouveaumotdepasse1\","
                                + "\"passwordConfirmation\":\"nouveaumotdepasse1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }
}
