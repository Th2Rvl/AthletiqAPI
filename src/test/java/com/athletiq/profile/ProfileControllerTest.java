package com.athletiq.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.profile.dto.CoachProfileResponse;
import com.athletiq.profile.dto.UpdateProfileRequest;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProfileController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class ProfileControllerTest {

    private static final String PROFILE = "/api/v1/profile";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    /** Requête d'un utilisateur authentifié, avec le rôle voulu. */
    private static RequestPostProcessor as(Role role) {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString())
                        .claim("roles", List.of(role.name())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Test
    void returnsTheProfileOfTheConnectedCoach() throws Exception {
        UUID id = UUID.randomUUID();
        when(profileService.get()).thenReturn(new CoachProfileResponse(id, "claire.martin@club.fr", "Claire", "Martin",
                Role.COACH, Instant.parse("2026-10-01T08:00:00Z")));

        mockMvc.perform(get(PROFILE).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("claire.martin@club.fr"))
                .andExpect(jsonPath("$.role").value("COACH"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void updatesTheProfile() throws Exception {
        UUID id = UUID.randomUUID();
        when(profileService.update(any(UpdateProfileRequest.class)))
                .thenReturn(new CoachProfileResponse(id, "claire.martin@club.fr", "Claire", "Martin",
                        Role.COACH, Instant.parse("2026-10-01T08:00:00Z")));

        mockMvc.perform(patch(PROFILE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"claire.martin@club.fr\",\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Martin"));
    }

    @Test
    void refusesTheProfileWithoutToken() throws Exception {
        mockMvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    @Test
    void refusesTheProfileOfAnAthlete() throws Exception {
        mockMvc.perform(get(PROFILE).with(as(Role.ATHLETE))).andExpect(status().isForbidden());
        verifyNoInteractions(profileService);
    }

    @Test
    void validatesTheProfileUpdate() throws Exception {
        mockMvc.perform(patch(PROFILE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\",\"firstName\":\"\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(profileService);
    }
}
