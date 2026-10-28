package com.athletiq.performance;

import static com.athletiq.support.TestJwt.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.performance.dto.AthletePerformanceResponse;
import com.athletiq.performance.dto.PerformedSetValue;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AthletePerformanceController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AthletePerformanceControllerTest {

    private static final UUID ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120002");
    private static final String URL = "/api/v1/me/planned-sessions/" + ID + "/performance";
    private static final String BODY = "{\"sets\":[{\"snapshotSetId\":\"" + ID + "\",\"reps\":8,\"loadKg\":72.5}],"
            + "\"rpe\":7,\"fatigue\":3,\"comment\":\"RAS\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AthletePerformanceService service;

    private static AthletePerformanceResponse validated() {
        return new AthletePerformanceResponse(PerformanceStatus.VALIDATED, 7, 3, "RAS",
                Instant.parse("2026-10-28T10:00:00Z"), null,
                List.of(new PerformedSetValue(ID, 8, new BigDecimal("72.5"))));
    }

    @Test
    void refusesAnonymousCoachAndAdmin() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (Role role : List.of(Role.COACH, Role.ADMIN)) {
            mockMvc.perform(get(URL).with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(put(URL).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(URL + "/validate").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void readsSavesAndValidates() throws Exception {
        when(service.get(ID)).thenReturn(AthletePerformanceResponse.none());
        when(service.save(any(), any())).thenReturn(validated());
        when(service.validate(any(), any())).thenReturn(validated());

        mockMvc.perform(get(URL).with(as(Role.ATHLETE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.sets").isEmpty());
        mockMvc.perform(put(URL).with(as(Role.ATHLETE)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.sets[0].loadKg").value(72.5));
        mockMvc.perform(post(URL + "/validate").with(as(Role.ATHLETE)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(service).validate(any(), any());
    }

    @Test
    void validatesTheBody() throws Exception {
        String[] bad = {
                "{\"rpe\":7}",
                "{\"sets\":[],\"rpe\":11}",
                "{\"sets\":[],\"rpe\":0}",
                "{\"sets\":[],\"fatigue\":6}",
                "{\"sets\":[{\"snapshotSetId\":\"" + ID + "\",\"reps\":0}]}",
                "{\"sets\":[{\"snapshotSetId\":\"" + ID + "\",\"reps\":101}]}",
                "{\"sets\":[{\"snapshotSetId\":\"" + ID + "\",\"reps\":8,\"loadKg\":72.3}]}",
                "{\"sets\":[{\"snapshotSetId\":\"" + ID + "\",\"reps\":8,\"loadKg\":-1}]}",
                "{\"sets\":[{\"reps\":8}]}",
                "{\"sets\":[],\"comment\":\"" + "x".repeat(1001) + "\"}",
        };
        for (String body : bad) {
            mockMvc.perform(put(URL).with(as(Role.ATHLETE)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void mapsBusinessErrors() throws Exception {
        when(service.validate(any(), any())).thenThrow(PerformanceErrors.performanceEmpty());
        mockMvc.perform(post(URL + "/validate").with(as(Role.ATHLETE)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_EMPTY"))
                .andExpect(jsonPath("$.field").value("sets"));

        when(service.save(any(), any())).thenThrow(PerformanceErrors.performedSetUnknown());
        mockMvc.perform(put(URL).with(as(Role.ATHLETE)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERFORMED_SET_UNKNOWN"));
    }
}
