package com.athletiq.performance;

import static com.athletiq.support.TestJwt.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.performance.dto.AthleteKpisResponse;
import com.athletiq.performance.dto.AthletePersonalRecordsResponse;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AthleteTrackingController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AthleteTrackingControllerTest {

    private static final UUID ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120002");
    private static final List<String> URIS = List.of("/api/v1/me/session-history", "/api/v1/me/personal-records",
            "/api/v1/me/performed-exercises", "/api/v1/me/exercises/" + ID + "/progression", "/api/v1/me/kpis");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AthleteTrackingService service;

    @Test
    void refusesAnonymousCoachAndAdmin() throws Exception {
        for (String uri : URIS) {
            mockMvc.perform(get(uri)).andExpect(status().isUnauthorized());
            for (Role role : List.of(Role.COACH, Role.ADMIN)) {
                mockMvc.perform(get(uri).with(as(role))).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void readsThePagedHistoryWithItsFilters() throws Exception {
        when(service.sessionHistory(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/me/session-history").with(as(Role.ATHLETE))
                        .param("from", "2026-10-01").param("to", "2026-10-28").param("status", "MISSED")
                        .param("withComment", "true").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        verify(service).sessionHistory(eq(new com.athletiq.performance.dto.SessionHistoryFilter(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28),
                com.athletiq.planning.SessionStatus.MISSED, true)), any());
    }

    @Test
    void refusesMalformedHistoryParameters() throws Exception {
        for (String query : List.of("size=0", "size=101", "page=-1", "from=demain", "status=ANNULEE")) {
            mockMvc.perform(get("/api/v1/me/session-history?" + query).with(as(Role.ATHLETE)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void usesTheDocumentedDefaultPeriods() throws Exception {
        when(service.kpis(30)).thenReturn(new AthleteKpisResponse(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 10, 27), 30, 3, 2, 60, new java.math.BigDecimal("7.0"), 7, 2));
        when(service.progression(eq(ID), eq(12))).thenThrow(new InvalidParameterException("weeks"));
        when(service.personalRecords()).thenReturn(new AthletePersonalRecordsResponse(ID, List.of()));

        mockMvc.perform(get("/api/v1/me/kpis").with(as(Role.ATHLETE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionRate").value(60))
                .andExpect(jsonPath("$.averageRpe").value(7.0))
                .andExpect(jsonPath("$.recentMissedCount").value(2));
        mockMvc.perform(get("/api/v1/me/exercises/" + ID + "/progression").with(as(Role.ATHLETE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/v1/me/personal-records").with(as(Role.ATHLETE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isEmpty());
    }

    @Test
    void mapsTheNoCoachRefusal() throws Exception {
        when(service.kpis(30)).thenThrow(AthleteErrors.athleteWithoutCoach());

        mockMvc.perform(get("/api/v1/me/kpis").with(as(Role.ATHLETE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATHLETE_WITHOUT_COACH"));
    }
}
