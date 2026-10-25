package com.athletiq.performance;

import static com.athletiq.support.TestJwt.as;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.group.GroupErrors;
import com.athletiq.performance.dto.AthleteKpi;
import com.athletiq.performance.dto.CoachKpisResponse;
import com.athletiq.performance.dto.KpiTotals;
import com.athletiq.performance.dto.WatchReason;
import com.athletiq.performance.dto.WatchlistEntry;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(KpiController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class KpiControllerTest {

    private static final String KPIS = "/api/v1/dashboard/kpis";
    private static final UUID GROUP_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120201");
    private static final UUID LUCAS = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120202");
    private static final UUID EMMA = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120203");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KpiService kpiService;

    private static CoachKpisResponse kpis(int weeks, UUID groupId) {
        return new CoachKpisResponse(TODAY, TODAY.minusWeeks(weeks), TODAY.minusDays(1), weeks, groupId,
                new KpiTotals(2, 5, 2, 71, new BigDecimal("7.3")),
                List.of(new AthleteKpi(LUCAS, "Lucas", "Girard", 2, 2, 50, new BigDecimal("7.0"), 1, true),
                        new AthleteKpi(EMMA, "Emma", "Lefèvre", 3, 0, 100, new BigDecimal("7.5"), 0, false)),
                List.of(new WatchlistEntry(LUCAS, "Lucas", "Girard", 2, 1, 50,
                        List.of(WatchReason.MISSED_RECENTLY, WatchReason.LOW_COMPLETION))));
    }

    @Test
    void refusesAnonymousAdminAndAthlete() throws Exception {
        mockMvc.perform(get(KPIS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHENTICATED));
        for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
            mockMvc.perform(get(KPIS).with(as(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
        }
        verifyNoInteractions(kpiService);
    }

    @Test
    void theKpisDefaultToFourWeeksForEveryAthlete() throws Exception {
        when(kpiService.kpis(4, null)).thenReturn(kpis(4, null));

        mockMvc.perform(get(KPIS).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-11-05"))
                .andExpect(jsonPath("$.from").value("2026-10-08"))
                .andExpect(jsonPath("$.to").value("2026-11-04"))
                .andExpect(jsonPath("$.weeks").value(4))
                .andExpect(jsonPath("$.groupId").isEmpty())
                .andExpect(jsonPath("$.totals.athleteCount").value(2))
                .andExpect(jsonPath("$.totals.doneCount").value(5))
                .andExpect(jsonPath("$.totals.missedCount").value(2))
                .andExpect(jsonPath("$.totals.completionRate").value(71))
                .andExpect(jsonPath("$.totals.averageRpe").value(7.3))
                .andExpect(jsonPath("$.athletes", hasSize(2)))
                .andExpect(jsonPath("$.athletes[0].athleteId").value(LUCAS.toString()))
                .andExpect(jsonPath("$.athletes[0].missedLast7Days").value(1))
                .andExpect(jsonPath("$.athletes[0].watch").value(true))
                .andExpect(jsonPath("$.athletes[1].completionRate").value(100))
                .andExpect(jsonPath("$.watchlist", hasSize(1)))
                .andExpect(jsonPath("$.watchlist[0].lastName").value("Girard"))
                .andExpect(jsonPath("$.watchlist[0].reasons[0]").value("MISSED_RECENTLY"))
                .andExpect(jsonPath("$.watchlist[0].reasons[1]").value("LOW_COMPLETION"));
    }

    @Test
    void theGroupAndThePeriodAreHandedToTheService() throws Exception {
        when(kpiService.kpis(12, GROUP_ID)).thenReturn(kpis(12, GROUP_ID));

        mockMvc.perform(get(KPIS).with(as(Role.COACH)).param("weeks", "12").param("groupId", GROUP_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks").value(12))
                .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()));
    }

    @Test
    void anUnknownPeriodOrAMalformedParameterAnswers400() throws Exception {
        when(kpiService.kpis(anyInt(), isNull())).thenThrow(new InvalidParameterException("weeks"));

        mockMvc.perform(get(KPIS).with(as(Role.COACH)).param("weeks", "52"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get(KPIS).with(as(Role.COACH)).param("weeks", "quatre"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get(KPIS).with(as(Role.COACH)).param("groupId", "pas-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
    }

    @Test
    void theGroupOfAnotherCoachAnswers404() throws Exception {
        when(kpiService.kpis(4, GROUP_ID)).thenThrow(GroupErrors.groupNotFound());

        mockMvc.perform(get(KPIS).with(as(Role.COACH)).param("groupId", GROUP_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(GroupErrors.GROUP_NOT_FOUND));
    }
}
