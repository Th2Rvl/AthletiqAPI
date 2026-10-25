package com.athletiq.performance;

import static com.athletiq.support.TestJwt.as;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.exercise.ExerciseErrors;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.performance.dto.PerformedExerciseResponse;
import com.athletiq.performance.dto.PersonalRecord;
import com.athletiq.performance.dto.PersonalRecordsResponse;
import com.athletiq.performance.dto.ProgressionPoint;
import com.athletiq.performance.dto.ProgressionResponse;
import com.athletiq.performance.dto.ReviewExercise;
import com.athletiq.performance.dto.ReviewSet;
import com.athletiq.performance.dto.SessionFeedback;
import com.athletiq.performance.dto.SessionHistoryFilter;
import com.athletiq.performance.dto.SessionHistoryItem;
import com.athletiq.performance.dto.SessionReviewResponse;
import com.athletiq.performance.dto.TrackingOverviewResponse;
import com.athletiq.planning.PlanningErrors;
import com.athletiq.planning.SessionStatus;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PerformanceController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class PerformanceControllerTest {

    private static final UUID ATHLETE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120101");
    private static final UUID SESSION_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120102");
    private static final UUID EXERCISE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120103");
    private static final UUID SNAPSHOT_EXERCISE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120104");
    private static final UUID SNAPSHOT_SET_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120105");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate DATE = LocalDate.of(2026, 11, 3);
    private static final Instant VALIDATED_AT = Instant.parse("2026-11-03T18:00:00Z");
    private static final Instant CORRECTED_AT = Instant.parse("2026-11-04T07:30:00Z");

    private static final String ATHLETE = "/api/v1/athletes/" + ATHLETE_ID;
    private static final String TRACKING = ATHLETE + "/tracking";
    private static final String HISTORY = ATHLETE + "/session-history";
    private static final String REVIEW = "/api/v1/planned-sessions/" + SESSION_ID + "/review";
    private static final String PERFORMED_EXERCISES = ATHLETE + "/performed-exercises";
    private static final String PROGRESSION = ATHLETE + "/exercises/" + EXERCISE_ID + "/progression";
    private static final String RECORDS = ATHLETE + "/personal-records";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PerformanceService performanceService;

    static Stream<Arguments> allEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, TRACKING),
                Arguments.of(HttpMethod.GET, HISTORY),
                Arguments.of(HttpMethod.GET, REVIEW),
                Arguments.of(HttpMethod.GET, PERFORMED_EXERCISES),
                Arguments.of(HttpMethod.GET, PROGRESSION),
                Arguments.of(HttpMethod.GET, RECORDS));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allEndpoints")
    void refusesAnonymousAdminAndAthlete(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHENTICATED));
        for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
            mockMvc.perform(request(method, uri).with(as(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
        }
        verifyNoInteractions(performanceService);
    }

    // ---------------------------------------------------------------- en-tête

    @Test
    void theOverviewAnswersTheAthleteTodayAndWhetherHeHasAHistory() throws Exception {
        when(performanceService.overview(ATHLETE_ID))
                .thenReturn(new TrackingOverviewResponse(ATHLETE_ID, "Lucas", "Girard", TODAY, true));

        mockMvc.perform(get(TRACKING).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteId").value(ATHLETE_ID.toString()))
                .andExpect(jsonPath("$.firstName").value("Lucas"))
                .andExpect(jsonPath("$.lastName").value("Girard"))
                .andExpect(jsonPath("$.today").value("2026-11-05"))
                .andExpect(jsonPath("$.hasPastSessions").value(true));
    }

    @Test
    void anAthleteNotFollowedAnswers404AsAProblemDetail() throws Exception {
        when(performanceService.overview(ATHLETE_ID)).thenThrow(AthleteErrors.athleteNotFound());

        mockMvc.perform(get(TRACKING).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(AthleteErrors.ATHLETE_NOT_FOUND));
    }

    // ---------------------------------------------------------------- US27, US29 historique

    @Test
    void theHistoryAnswersAPageWithTheDefaultParameters() throws Exception {
        SessionHistoryFilter defaults = new SessionHistoryFilter(null, null, null, false);
        when(performanceService.sessionHistory(ATHLETE_ID, defaults, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(
                List.of(new SessionHistoryItem(SESSION_ID, DATE, "Haut du corps A", SessionStatus.DONE, 8, null, true,
                                VALIDATED_AT, CORRECTED_AT),
                        new SessionHistoryItem(UUID.randomUUID(), DATE.minusDays(2), "Bas du corps A",
                                SessionStatus.MISSED, null, null, false, null, null)),
                PageRequest.of(0, 20), 2));

        mockMvc.perform(get(HISTORY).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].plannedSessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.content[0].status").value("DONE"))
                .andExpect(jsonPath("$.content[0].rpe").value(8))
                .andExpect(jsonPath("$.content[0].fatigue").isEmpty())
                .andExpect(jsonPath("$.content[0].hasComment").value(true))
                .andExpect(jsonPath("$.content[0].validatedAt").value("2026-11-03T18:00:00Z"))
                .andExpect(jsonPath("$.content[0].correctedAt").value("2026-11-04T07:30:00Z"))
                .andExpect(jsonPath("$.content[1].status").value("MISSED"))
                .andExpect(jsonPath("$.content[1].validatedAt").isEmpty())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void theHistoryHandsTheFiltersAndThePageToTheService() throws Exception {
        when(performanceService.sessionHistory(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(HISTORY).with(as(Role.COACH))
                        .param("from", "2026-09-01").param("to", "2026-10-31").param("status", "DONE")
                        .param("withComment", "true").param("page", "2").param("size", "100"))
                .andExpect(status().isOk());

        verify(performanceService).sessionHistory(ATHLETE_ID, new SessionHistoryFilter(LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31), SessionStatus.DONE, true), PageRequest.of(2, 100));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"from=2026-13-01", "to=05/11/2026", "status=PLANIFIEE", "status=done",
            "withComment=peut-etre", "page=-1", "size=0", "size=101"})
    void aMalformedHistoryParameterAnswers400InvalidParameter(String parameter) throws Exception {
        String[] pair = parameter.split("=", 2);

        mockMvc.perform(get(HISTORY).with(as(Role.COACH)).param(pair[0], pair[1]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        verifyNoInteractions(performanceService);
    }

    @Test
    void aPeriodRefusedByTheServiceAnswers400InvalidParameter() throws Exception {
        when(performanceService.sessionHistory(any(), any(), any())).thenThrow(new InvalidParameterException("to"));

        mockMvc.perform(get(HISTORY).with(as(Role.COACH)).param("from", "2025-01-01").param("to", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER))
                .andExpect(jsonPath("$.detail").value("Paramètre invalide : to"));
    }

    @Test
    void aMalformedAthleteIdAnswers400() throws Exception {
        mockMvc.perform(get("/api/v1/athletes/pas-un-uuid/session-history").with(as(Role.COACH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        verifyNoInteractions(performanceService);
    }

    // ---------------------------------------------------------------- US28, US29 prévu / réalisé

    private static SessionReviewResponse review(SessionStatus status, SessionFeedback feedback, boolean done) {
        ReviewSet set = done
                ? new ReviewSet(SNAPSHOT_SET_ID, 0, 8, new BigDecimal("60.0"), 90, true, 8, new BigDecimal("62.5"), 0,
                new BigDecimal("2.5"))
                : new ReviewSet(SNAPSHOT_SET_ID, 0, 8, new BigDecimal("60.0"), 90, false, null, null, null, null);
        ReviewExercise exercise = new ReviewExercise(SNAPSHOT_EXERCISE_ID, EXERCISE_ID, "Développé couché",
                MuscleGroup.PECTORAUX, 0, "Tempo 3-1-1", false, new BigDecimal("480.0"),
                done ? new BigDecimal("500.0") : null, done ? 4 : null, List.of(set));
        return new SessionReviewResponse(SESSION_ID, ATHLETE_ID, "Lucas", "Girard", DATE, TODAY, "Haut du corps A",
                "Consignes", status, feedback, List.of(exercise));
    }

    @Test
    void theReviewOfADoneSessionAnswersThePlannedThePerformedAndTheFeedback() throws Exception {
        when(performanceService.review(SESSION_ID)).thenReturn(review(SessionStatus.DONE,
                new SessionFeedback(8, 4, "Dernière série difficile", VALIDATED_AT, CORRECTED_AT), true));

        mockMvc.perform(get(REVIEW).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedSessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.athleteFirstName").value("Lucas"))
                .andExpect(jsonPath("$.today").value("2026-11-05"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.feedback.rpe").value(8))
                .andExpect(jsonPath("$.feedback.fatigue").value(4))
                .andExpect(jsonPath("$.feedback.comment").value("Dernière série difficile"))
                .andExpect(jsonPath("$.feedback.correctedAt").value("2026-11-04T07:30:00Z"))
                .andExpect(jsonPath("$.exercises[0].snapshotExerciseId").value(SNAPSHOT_EXERCISE_ID.toString()))
                .andExpect(jsonPath("$.exercises[0].exerciseId").value(EXERCISE_ID.toString()))
                .andExpect(jsonPath("$.exercises[0].muscleGroup").value("PECTORAUX"))
                .andExpect(jsonPath("$.exercises[0].bodyweight").value(false))
                .andExpect(jsonPath("$.exercises[0].plannedVolumeKg").value(480.0))
                .andExpect(jsonPath("$.exercises[0].performedVolumeKg").value(500.0))
                .andExpect(jsonPath("$.exercises[0].volumeDeltaPercent").value(4))
                .andExpect(jsonPath("$.exercises[0].sets[0].snapshotSetId").value(SNAPSHOT_SET_ID.toString()))
                .andExpect(jsonPath("$.exercises[0].sets[0].plannedLoadKg").value(60.0))
                .andExpect(jsonPath("$.exercises[0].sets[0].done").value(true))
                .andExpect(jsonPath("$.exercises[0].sets[0].performedLoadKg").value(62.5))
                .andExpect(jsonPath("$.exercises[0].sets[0].repsDelta").value(0))
                .andExpect(jsonPath("$.exercises[0].sets[0].loadDeltaKg").value(2.5));
    }

    @Test
    void theReviewOfAMissedSessionHasNoFeedbackNorPerformed() throws Exception {
        when(performanceService.review(SESSION_ID)).thenReturn(review(SessionStatus.MISSED, null, false));

        mockMvc.perform(get(REVIEW).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISSED"))
                .andExpect(jsonPath("$.feedback").isEmpty())
                .andExpect(jsonPath("$.exercises[0].performedVolumeKg").isEmpty())
                .andExpect(jsonPath("$.exercises[0].sets[0].done").value(false))
                .andExpect(jsonPath("$.exercises[0].sets[0].performedReps").isEmpty());
    }

    @Test
    void theReviewOfAnotherCoachsSessionAnswers404AndAMalformedIdAnswers400() throws Exception {
        when(performanceService.review(SESSION_ID)).thenThrow(PlanningErrors.plannedSessionNotFound());

        mockMvc.perform(get(REVIEW).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(PlanningErrors.PLANNED_SESSION_NOT_FOUND));
        mockMvc.perform(get("/api/v1/planned-sessions/pas-un-uuid/review").with(as(Role.COACH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
    }

    // ---------------------------------------------------------------- US30 exercices réalisés et progression

    @Test
    void thePerformedExercisesAnswerAList() throws Exception {
        when(performanceService.performedExercises(ATHLETE_ID)).thenReturn(List.of(
                new PerformedExerciseResponse(EXERCISE_ID, "Squat", MuscleGroup.QUADRICEPS, 6, DATE)));

        mockMvc.perform(get(PERFORMED_EXERCISES).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].exerciseId").value(EXERCISE_ID.toString()))
                .andExpect(jsonPath("$[0].exerciseName").value("Squat"))
                .andExpect(jsonPath("$[0].muscleGroup").value("QUADRICEPS"))
                .andExpect(jsonPath("$[0].sessionCount").value(6))
                .andExpect(jsonPath("$[0].lastPerformedOn").value("2026-11-03"));
    }

    @Test
    void theProgressionDefaultsToTwelveWeeksAndAnswersThePoints() throws Exception {
        when(performanceService.progression(ATHLETE_ID, EXERCISE_ID, 12)).thenReturn(new ProgressionResponse(
                ATHLETE_ID, EXERCISE_ID, "Squat", MuscleGroup.QUADRICEPS, false, 12, TODAY.minusDays(84), TODAY,
                List.of(new ProgressionPoint(SESSION_ID, DATE, new BigDecimal("110.0"), 5,
                        new BigDecimal("1330.0")))));

        mockMvc.perform(get(PROGRESSION).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseName").value("Squat"))
                .andExpect(jsonPath("$.bodyweight").value(false))
                .andExpect(jsonPath("$.weeks").value(12))
                .andExpect(jsonPath("$.from").value("2026-08-13"))
                .andExpect(jsonPath("$.today").value("2026-11-05"))
                .andExpect(jsonPath("$.points[0].plannedSessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.points[0].date").value("2026-11-03"))
                .andExpect(jsonPath("$.points[0].maxLoadKg").value(110.0))
                .andExpect(jsonPath("$.points[0].maxReps").value(5))
                .andExpect(jsonPath("$.points[0].volumeKg").value(1330.0));
    }

    @Test
    void theProgressionPeriodIsHandedToTheServiceWhichRefusesAnUnknownOne() throws Exception {
        when(performanceService.progression(ATHLETE_ID, EXERCISE_ID, 5)).thenThrow(new InvalidParameterException("weeks"));

        mockMvc.perform(get(PROGRESSION).with(as(Role.COACH)).param("weeks", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER))
                .andExpect(jsonPath("$.detail").value("Paramètre invalide : weeks"));
        mockMvc.perform(get(PROGRESSION).with(as(Role.COACH)).param("weeks", "douze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get(ATHLETE + "/exercises/pas-un-uuid/progression").with(as(Role.COACH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
    }

    @Test
    void theProgressionOfAnotherCoachsExerciseOrAnAthleteNotFollowedAnswers404() throws Exception {
        when(performanceService.progression(ATHLETE_ID, EXERCISE_ID, 12))
                .thenThrow(ExerciseErrors.exerciseNotFound())
                .thenThrow(AthleteErrors.athleteNotFound());

        mockMvc.perform(get(PROGRESSION).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ExerciseErrors.EXERCISE_NOT_FOUND));
        mockMvc.perform(get(PROGRESSION).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AthleteErrors.ATHLETE_NOT_FOUND));
    }

    // ---------------------------------------------------------------- US31 records

    @Test
    void theRecordsAnswerTheBestSetOfEachExercise() throws Exception {
        when(performanceService.personalRecords(ATHLETE_ID)).thenReturn(new PersonalRecordsResponse(ATHLETE_ID, List.of(
                new PersonalRecord(EXERCISE_ID, "Tractions", MuscleGroup.DOS, true, null, 12, DATE, SESSION_ID),
                new PersonalRecord(UUID.randomUUID(), "Squat", MuscleGroup.QUADRICEPS, false,
                        new BigDecimal("110.0"), 5, DATE.minusDays(7), UUID.randomUUID()))));

        mockMvc.perform(get(RECORDS).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteId").value(ATHLETE_ID.toString()))
                .andExpect(jsonPath("$.records", hasSize(2)))
                .andExpect(jsonPath("$.records[0].bodyweight").value(true))
                .andExpect(jsonPath("$.records[0].loadKg").isEmpty())
                .andExpect(jsonPath("$.records[0].reps").value(12))
                .andExpect(jsonPath("$.records[0].plannedSessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.records[1].loadKg").value(110.0))
                .andExpect(jsonPath("$.records[1].date").value("2026-10-27"));
    }

    @Test
    void theRecordsOfAnAthleteNotFollowedAnswer404() throws Exception {
        when(performanceService.personalRecords(ATHLETE_ID)).thenThrow(AthleteErrors.athleteNotFound());

        mockMvc.perform(get(RECORDS).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AthleteErrors.ATHLETE_NOT_FOUND));
    }
}
