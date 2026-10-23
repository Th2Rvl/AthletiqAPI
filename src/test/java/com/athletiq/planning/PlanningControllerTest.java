package com.athletiq.planning;

import static com.athletiq.support.TestJwt.as;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.group.GroupErrors;
import com.athletiq.planning.dto.AthleteCalendarResponse;
import com.athletiq.planning.dto.CreatePlannedSessionRequest;
import com.athletiq.planning.dto.CreatedCountResponse;
import com.athletiq.planning.dto.DuplicateWeekRequest;
import com.athletiq.planning.dto.GroupCalendarEntry;
import com.athletiq.planning.dto.GroupCalendarResponse;
import com.athletiq.planning.dto.GroupCalendarSession;
import com.athletiq.planning.dto.MovePlannedSessionRequest;
import com.athletiq.planning.dto.PlannedSessionDetailResponse;
import com.athletiq.planning.dto.PlannedSessionSummary;
import com.athletiq.planning.dto.UpcomingCountResponse;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import com.athletiq.workout.WorkoutErrors;
import com.athletiq.workout.dto.ExerciseRef;
import com.athletiq.workout.dto.PlannedSetResponse;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlanningController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class PlanningControllerTest {

    private static final UUID ATHLETE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120001");
    private static final UUID GROUP_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120002");
    private static final UUID WORKOUT_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120003");
    private static final UUID SESSION_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120004");
    private static final UUID EXERCISE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120005");
    private static final UUID SNAPSHOT_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120006");
    private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 12);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private static final String ATHLETE_SESSIONS = "/api/v1/athletes/" + ATHLETE_ID + "/planned-sessions";
    private static final String GROUP_SESSIONS = "/api/v1/groups/" + GROUP_ID + "/planned-sessions";
    private static final String ONE = "/api/v1/planned-sessions/" + SESSION_ID;
    private static final String PLAN_BODY = "{\"workoutId\":\"" + WORKOUT_ID + "\",\"date\":\"2026-11-12\"}";
    private static final String DUPLICATE_BODY = "{\"weekStart\":\"2026-11-02\",\"weeks\":3}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanningService planningService;

    private static PlannedSessionDetailResponse detail(LocalDate date) {
        return new PlannedSessionDetailResponse(SESSION_ID, ATHLETE_ID, "Lucas", "Girard", date, null, WORKOUT_ID,
                false, "Haut du corps A", null, List.of(new WorkoutExerciseResponse(0,
                new ExerciseRef(EXERCISE_ID, "Développé couché", MuscleGroup.PECTORAUX), "Tempo 3-1-1",
                List.of(new PlannedSetResponse(0, 8, new BigDecimal("60.0"), 90),
                        new PlannedSetResponse(1, 8, null, null)))), NOW, NOW);
    }

    static Stream<Arguments> allEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, ATHLETE_SESSIONS + "?from=2026-11-02&to=2026-11-29"),
                Arguments.of(HttpMethod.POST, ATHLETE_SESSIONS),
                Arguments.of(HttpMethod.GET, ATHLETE_SESSIONS + "/upcoming-count"),
                Arguments.of(HttpMethod.POST, ATHLETE_SESSIONS + "/duplicate-week"),
                Arguments.of(HttpMethod.GET, GROUP_SESSIONS + "?from=2026-11-02&to=2026-11-29"),
                Arguments.of(HttpMethod.POST, GROUP_SESSIONS),
                Arguments.of(HttpMethod.POST, GROUP_SESSIONS + "/duplicate-week"),
                Arguments.of(HttpMethod.GET, ONE),
                Arguments.of(HttpMethod.PATCH, ONE),
                Arguments.of(HttpMethod.DELETE, ONE));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allEndpoints")
    void refusesAnonymousAdminAndAthlete(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHENTICATED));
        for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
            mockMvc.perform(request(method, uri).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
        }
        verifyNoInteractions(planningService);
    }

    // ---------------------------------------------------------------- US21 athlète

    @Test
    void planningForAnAthleteAnswers201WithLocationAndTheDetail() throws Exception {
        when(planningService.planForAthlete(ATHLETE_ID, new CreatePlannedSessionRequest(WORKOUT_ID, DATE)))
                .thenReturn(detail(DATE));

        mockMvc.perform(post(ATHLETE_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/planned-sessions/" + SESSION_ID))
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.date").value("2026-11-12"))
                .andExpect(jsonPath("$.athleteFirstName").value("Lucas"))
                .andExpect(jsonPath("$.groupId").isEmpty())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.exercises[0].exercise.name").value("Développé couché"))
                .andExpect(jsonPath("$.exercises[0].exercise.muscleGroup").value("PECTORAUX"))
                .andExpect(jsonPath("$.exercises[0].sets[0].loadKg").value(60.0))
                .andExpect(jsonPath("$.exercises[0].sets[1].loadKg").isEmpty())
                .andExpect(jsonPath("$.createdAt").value("2026-11-05T08:00:00Z"));
    }

    @Test
    void aMissingWorkoutOrDateAnswers400WithTheFields() throws Exception {
        mockMvc.perform(post(ATHLETE_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'workoutId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'date')]").exists());
        verifyNoInteractions(planningService);
    }

    @Test
    void aMalformedDateOrUuidAnswers400MalformedRequest() throws Exception {
        for (String body : List.of("{\"workoutId\":\"" + WORKOUT_ID + "\",\"date\":\"12/11/2026\"}",
                "{\"workoutId\":\"" + WORKOUT_ID + "\",\"date\":\"2026-02-30\"}",
                "{\"workoutId\":\"pas-un-uuid\",\"date\":\"2026-11-12\"}")) {
            mockMvc.perform(post(ATHLETE_SESSIONS).with(as(Role.COACH))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.MALFORMED_REQUEST));
        }
        verifyNoInteractions(planningService);
    }

    @Test
    void refusalsOfTheServiceComeOutAsProblemDetails() throws Exception {
        when(planningService.planForAthlete(eq(ATHLETE_ID), any())).thenThrow(AthleteErrors.athleteNotFound())
                .thenThrow(WorkoutErrors.workoutNotFound())
                .thenThrow(PlanningErrors.dateInPast());

        mockMvc.perform(post(ATHLETE_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AthleteErrors.ATHLETE_NOT_FOUND));
        mockMvc.perform(post(ATHLETE_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(WorkoutErrors.WORKOUT_NOT_FOUND));
        mockMvc.perform(post(ATHLETE_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(PlanningErrors.PLANNING_DATE_IN_PAST))
                .andExpect(jsonPath("$.field").value("date"))
                .andExpect(jsonPath("$.detail").value("Impossible de planifier à une date passée"));
    }

    // ---------------------------------------------------------------- US22 groupe

    @Test
    void planningForAGroupAnswers201WithoutLocationAndTheCount() throws Exception {
        when(planningService.planForGroup(GROUP_ID, new CreatePlannedSessionRequest(WORKOUT_ID, DATE)))
                .thenReturn(new CreatedCountResponse(3));

        mockMvc.perform(post(GROUP_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.created").value(3));
    }

    @Test
    void anEmptyGroupOrTheGroupOfAnotherCoachIsRefused() throws Exception {
        when(planningService.planForGroup(eq(GROUP_ID), any())).thenThrow(PlanningErrors.groupHasNoMembers())
                .thenThrow(GroupErrors.groupNotFound());

        mockMvc.perform(post(GROUP_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(PlanningErrors.GROUP_HAS_NO_MEMBERS));
        mockMvc.perform(post(GROUP_SESSIONS).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(GroupErrors.GROUP_NOT_FOUND));
    }

    // ---------------------------------------------------------------- US24 calendriers

    @Test
    void theAthleteCalendarAnswersTheSessionsAndToday() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 2);
        LocalDate to = LocalDate.of(2026, 11, 29);
        when(planningService.athleteCalendar(ATHLETE_ID, from, to)).thenReturn(new AthleteCalendarResponse(ATHLETE_ID,
                from, to, TODAY, List.of(
                new PlannedSessionSummary(SESSION_ID, ATHLETE_ID, DATE, "Haut du corps A", 5, 15, null, false),
                new PlannedSessionSummary(UUID.randomUUID(), ATHLETE_ID, DATE, "Bas du corps A", 2, 6, GROUP_ID,
                        false))));

        mockMvc.perform(get(ATHLETE_SESSIONS).with(as(Role.COACH)).param("from", "2026-11-02").param("to", "2026-11-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-11-05"))
                .andExpect(jsonPath("$.from").value("2026-11-02"))
                .andExpect(jsonPath("$.sessions", hasSize(2)))
                .andExpect(jsonPath("$.sessions[0].date").value("2026-11-12"))
                .andExpect(jsonPath("$.sessions[0].exerciseCount").value(5))
                .andExpect(jsonPath("$.sessions[0].setCount").value(15))
                .andExpect(jsonPath("$.sessions[0].locked").value(false))
                .andExpect(jsonPath("$.sessions[1].groupId").value(GROUP_ID.toString()));
    }

    @Test
    void aMissingOrMalformedPeriodAnswers400InvalidParameter() throws Exception {
        mockMvc.perform(get(ATHLETE_SESSIONS).with(as(Role.COACH)).param("from", "2026-11-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER))
                .andExpect(jsonPath("$.detail").value("Paramètre invalide : to"));
        mockMvc.perform(get(GROUP_SESSIONS).with(as(Role.COACH)).param("from", "02/11/2026").param("to", "2026-11-29"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get("/api/v1/athletes/pas-un-uuid/planned-sessions").with(as(Role.COACH))
                        .param("from", "2026-11-02").param("to", "2026-11-29"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        verifyNoInteractions(planningService);
    }

    @Test
    void aPeriodRefusedByTheServiceAnswers400InvalidParameter() throws Exception {
        when(planningService.athleteCalendar(any(), any(), any())).thenThrow(new InvalidParameterException("to"));

        mockMvc.perform(get(ATHLETE_SESSIONS).with(as(Role.COACH)).param("from", "2026-11-02").param("to", "2027-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
    }

    @Test
    void theGroupCalendarAnswersEntriesWithNOverN() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 2);
        LocalDate to = LocalDate.of(2026, 11, 29);
        when(planningService.groupCalendar(GROUP_ID, from, to)).thenReturn(new GroupCalendarResponse(GROUP_ID, from,
                to, TODAY, 4, List.of(new GroupCalendarEntry(DATE, SNAPSHOT_ID, "Haut du corps A", 3, 4, false,
                List.of(new GroupCalendarSession(SESSION_ID, ATHLETE_ID, "Lucas", "Durand"))))));

        mockMvc.perform(get(GROUP_SESSIONS).with(as(Role.COACH)).param("from", "2026-11-02").param("to", "2026-11-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(4))
                .andExpect(jsonPath("$.entries[0].snapshotId").value(SNAPSHOT_ID.toString()))
                .andExpect(jsonPath("$.entries[0].athleteCount").value(3))
                .andExpect(jsonPath("$.entries[0].memberCount").value(4))
                .andExpect(jsonPath("$.entries[0].sessions[0].lastName").value("Durand"));
    }

    @Test
    void theUpcomingCountAnswersACount() throws Exception {
        when(planningService.upcomingCount(ATHLETE_ID)).thenReturn(new UpcomingCountResponse(4));

        mockMvc.perform(get(ATHLETE_SESSIONS + "/upcoming-count").with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    // ---------------------------------------------------------------- US25 détail, déplacer, annuler

    @Test
    void getMoveAndCancelAnswer200200And204() throws Exception {
        when(planningService.get(SESSION_ID)).thenReturn(detail(DATE));
        when(planningService.move(SESSION_ID, new MovePlannedSessionRequest(DATE.plusDays(1))))
                .thenReturn(detail(DATE.plusDays(1)));

        mockMvc.perform(get(ONE).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Haut du corps A"));
        mockMvc.perform(patch(ONE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-13\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-13"));
        mockMvc.perform(delete(ONE).with(as(Role.COACH)))
                .andExpect(status().isNoContent());
        verify(planningService).cancel(SESSION_ID);
    }

    @Test
    void movingWithoutADateAnswers400() throws Exception {
        mockMvc.perform(patch(ONE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        verifyNoInteractions(planningService);
    }

    @Test
    void aLockedOrUnknownPlannedSessionIsRefused() throws Exception {
        doThrow(PlanningErrors.plannedSessionLocked()).when(planningService).cancel(SESSION_ID);
        when(planningService.get(SESSION_ID)).thenThrow(PlanningErrors.plannedSessionNotFound());

        mockMvc.perform(delete(ONE).with(as(Role.COACH)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(PlanningErrors.PLANNED_SESSION_LOCKED));
        mockMvc.perform(get(ONE).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(PlanningErrors.PLANNED_SESSION_NOT_FOUND))
                .andExpect(jsonPath("$.detail").value("Cette séance planifiée n'existe plus"));
    }

    // ---------------------------------------------------------------- US26 dupliquer

    @Test
    void duplicatingAWeekAnswers201WithTheCount() throws Exception {
        DuplicateWeekRequest request = new DuplicateWeekRequest(LocalDate.of(2026, 11, 2), 3);
        when(planningService.duplicateWeekForAthlete(ATHLETE_ID, request)).thenReturn(new CreatedCountResponse(9));
        when(planningService.duplicateWeekForGroup(GROUP_ID, request)).thenReturn(new CreatedCountResponse(12));

        mockMvc.perform(post(ATHLETE_SESSIONS + "/duplicate-week").with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(DUPLICATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.created").value(9));
        mockMvc.perform(post(GROUP_SESSIONS + "/duplicate-week").with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(DUPLICATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(12));
    }

    @Test
    void aWeekStartThatIsNotAMondayOrWeeksOutOfBoundsAnswer400() throws Exception {
        for (String body : List.of("{\"weekStart\":\"2026-11-03\",\"weeks\":3}",
                "{\"weekStart\":\"2026-11-02\",\"weeks\":0}",
                "{\"weekStart\":\"2026-11-02\",\"weeks\":13}")) {
            mockMvc.perform(post(GROUP_SESSIONS + "/duplicate-week").with(as(Role.COACH))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
                    .andExpect(jsonPath("$.errors", hasSize(1)));
        }
        mockMvc.perform(post(ATHLETE_SESSIONS + "/duplicate-week").with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
        verifyNoInteractions(planningService);
    }

    @Test
    void anEmptyWeekPastTargetsOrATooLargeBatchAnswer409() throws Exception {
        when(planningService.duplicateWeekForAthlete(eq(ATHLETE_ID), any()))
                .thenThrow(PlanningErrors.weekEmpty())
                .thenThrow(PlanningErrors.targetDateInPast())
                .thenThrow(PlanningErrors.batchTooLarge());

        for (String code : List.of(PlanningErrors.PLANNING_WEEK_EMPTY, PlanningErrors.PLANNING_DATE_IN_PAST,
                PlanningErrors.PLANNING_BATCH_TOO_LARGE)) {
            mockMvc.perform(post(ATHLETE_SESSIONS + "/duplicate-week").with(as(Role.COACH))
                            .contentType(MediaType.APPLICATION_JSON).content(DUPLICATE_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.field").doesNotExist());
        }
    }
}
