package com.athletiq.planning;

import static com.athletiq.support.TestJwt.as;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.planning.dto.AthleteSessionDetailResponse;
import com.athletiq.planning.dto.AthleteSessionListResponse;
import com.athletiq.planning.dto.AthleteSessionSummary;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AthletePlanningController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AthletePlanningControllerTest {

    private static final UUID ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120002");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AthletePlanningService service;

    @Test
    void refusesAnonymousCoachAndAdmin() throws Exception {
        for (String uri : List.of("/api/v1/me/planned-sessions/today", "/api/v1/me/planned-sessions/" + ID)) {
            mockMvc.perform(get(uri)).andExpect(status().isUnauthorized());
            for (Role role : List.of(Role.COACH, Role.ADMIN)) {
                mockMvc.perform(get(uri).with(as(role)))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void returnsTodaySessions() throws Exception {
        when(service.today()).thenReturn(new AthleteSessionListResponse(TODAY, TODAY, TODAY, List.of(
                new AthleteSessionSummary(ID, TODAY, "Haut du corps A", 2, 7, ID, "Nicolas", "Petit",
                        SessionStatus.UPCOMING))));

        mockMvc.perform(get("/api/v1/me/planned-sessions/today").with(as(Role.ATHLETE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-10-28"))
                .andExpect(jsonPath("$.sessions[0].name").value("Haut du corps A"))
                .andExpect(jsonPath("$.sessions[0].status").value("UPCOMING"));
    }

    @Test
    void readsACalendarPeriod() throws Exception {
        when(service.calendar(TODAY, TODAY.plusDays(6)))
                .thenReturn(new AthleteSessionListResponse(TODAY, TODAY.plusDays(6), TODAY, List.of()));

        mockMvc.perform(get("/api/v1/me/planned-sessions").with(as(Role.ATHLETE))
                        .param("from", "2026-10-28").param("to", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isEmpty());
        verify(service).calendar(TODAY, TODAY.plusDays(6));
    }

    @Test
    void refusesAMissingOrMalformedPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/me/planned-sessions").with(as(Role.ATHLETE)).param("from", "2026-10-28"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/planned-sessions").with(as(Role.ATHLETE))
                        .param("from", "demain").param("to", "2026-11-03"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void mapsBusinessErrors() throws Exception {
        when(service.get(ID)).thenThrow(PlanningErrors.plannedSessionNotFound());
        mockMvc.perform(get("/api/v1/me/planned-sessions/" + ID).with(as(Role.ATHLETE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_NOT_FOUND"));

        doThrow(AthleteErrors.athleteWithoutCoach()).when(service).today();
        mockMvc.perform(get("/api/v1/me/planned-sessions/today").with(as(Role.ATHLETE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATHLETE_WITHOUT_COACH"));
    }

    @Test
    void returnsASessionDetail() throws Exception {
        when(service.get(ID)).thenReturn(new AthleteSessionDetailResponse(ID, TODAY, TODAY, "Jambes", null, ID,
                "Nicolas", "Petit", SessionStatus.UPCOMING, List.of()));

        mockMvc.perform(get("/api/v1/me/planned-sessions/" + ID).with(as(Role.ATHLETE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jambes"))
                .andExpect(jsonPath("$.exercises").isEmpty());
    }
}
