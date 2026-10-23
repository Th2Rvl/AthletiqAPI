package com.athletiq.workout;

import static com.athletiq.support.TestJwt.as;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.exercise.ExerciseErrors;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import com.athletiq.workout.dto.ExerciseRef;
import com.athletiq.workout.dto.PlannedSetRequest;
import com.athletiq.workout.dto.PlannedSetResponse;
import com.athletiq.workout.dto.WorkoutExerciseRequest;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import com.athletiq.workout.dto.WorkoutRequest;
import com.athletiq.workout.dto.WorkoutResponse;
import com.athletiq.workout.dto.WorkoutSummaryResponse;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkoutController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class WorkoutControllerTest {

    private static final String BASE = "/api/v1/workouts";
    private static final UUID WORKOUT_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120020");
    private static final UUID COPY_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120021");
    private static final UUID EXERCISE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120010");
    private static final Instant NOW = Instant.parse("2026-10-23T08:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkoutService workoutService;

    private static String body(String name, String exercises) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"") + ",\"exercises\":" + exercises + "}";
    }

    private static String exercise(String sets) {
        return "{\"exerciseId\":\"" + EXERCISE_ID + "\",\"note\":\"Tempo 3-1-1\",\"sets\":" + sets + "}";
    }

    private static final String SET = "{\"reps\":8,\"loadKg\":60.5,\"restSeconds\":90}";
    private static final String VALID_BODY = body("Haut du corps A", "[" + exercise("[" + SET + "]") + "]");

    private static WorkoutResponse workout(UUID id, String name) {
        return new WorkoutResponse(id, name, null, List.of(new WorkoutExerciseResponse(0,
                new ExerciseRef(EXERCISE_ID, "Squat", MuscleGroup.QUADRICEPS), "Tempo 3-1-1",
                List.of(new PlannedSetResponse(0, 8, new BigDecimal("60.5"), 90),
                        new PlannedSetResponse(1, 8, null, null)))), NOW, NOW);
    }

    static Stream<Arguments> allEndpoints() {
        String one = BASE + "/" + WORKOUT_ID;
        return Stream.of(
                Arguments.of(HttpMethod.GET, BASE),
                Arguments.of(HttpMethod.POST, BASE),
                Arguments.of(HttpMethod.GET, one),
                Arguments.of(HttpMethod.PUT, one),
                Arguments.of(HttpMethod.POST, one + "/duplicate"),
                Arguments.of(HttpMethod.DELETE, one));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allEndpoints")
    void refusesAnonymousAdminAndAthlete(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHENTICATED));
        for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
            mockMvc.perform(request(method, uri).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
        }
        verifyNoInteractions(workoutService);
    }

    // ---------------------------------------------------------------- liste

    @Test
    void listsWithTheSearchAndAPagedModel() throws Exception {
        when(workoutService.list(eq("haut"), eq(PageRequest.of(1, 10)))).thenReturn(new PageImpl<>(
                List.of(new WorkoutSummaryResponse(WORKOUT_ID, "Haut du corps A", null, 3,
                        List.of(MuscleGroup.PECTORAUX, MuscleGroup.TRICEPS), NOW, NOW)),
                PageRequest.of(1, 10), 11));

        mockMvc.perform(get(BASE).with(as(Role.COACH))
                        .param("q", "haut").param("page", "1").param("size", "10").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Haut du corps A"))
                .andExpect(jsonPath("$.content[0].exerciseCount").value(3))
                .andExpect(jsonPath("$.content[0].muscleGroups[0]").value("PECTORAUX"))
                .andExpect(jsonPath("$.content[0].description").isEmpty())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(11))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void refusesAnOverlongSearchAndAPageOutOfBounds() throws Exception {
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        verifyNoInteractions(workoutService);
    }

    // ---------------------------------------------------------------- création

    @Test
    void createsAWorkoutWithItsLocationAndTheTrimmedRequest() throws Exception {
        when(workoutService.create(any())).thenReturn(workout(WORKOUT_ID, "Haut du corps A"));
        String sent = "{\"name\":\"  Haut du corps A \",\"description\":\"  \",\"exercises\":[{\"exerciseId\":\""
                + EXERCISE_ID + "\",\"note\":\" \",\"position\":7,\"sets\":[{\"reps\":8,\"loadKg\":60,"
                + "\"restSeconds\":90,\"position\":3},{\"reps\":8}]}]}";

        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(sent))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/" + WORKOUT_ID))
                .andExpect(jsonPath("$.id").value(WORKOUT_ID.toString()))
                .andExpect(jsonPath("$.exercises[0].position").value(0))
                .andExpect(jsonPath("$.exercises[0].exercise.name").value("Squat"))
                .andExpect(jsonPath("$.exercises[0].exercise.muscleGroup").value("QUADRICEPS"))
                .andExpect(jsonPath("$.exercises[0].id").doesNotExist())
                .andExpect(jsonPath("$.exercises[0].sets[0].loadKg").value(60.5))
                .andExpect(jsonPath("$.exercises[0].sets[1].loadKg").isEmpty())
                .andExpect(jsonPath("$.exercises[0].sets[1].restSeconds").isEmpty())
                .andExpect(jsonPath("$.exercises[0].sets[0].id").doesNotExist());
        verify(workoutService).create(new WorkoutRequest("Haut du corps A", null, List.of(
                new WorkoutExerciseRequest(EXERCISE_ID, null, List.of(
                        new PlannedSetRequest(8, new BigDecimal("60"), 90),
                        new PlannedSetRequest(8, null, null))))));
    }

    static Stream<Arguments> invalidBodies() {
        String one = "[" + exercise("[" + SET + "]") + "]";
        return Stream.of(
                Arguments.of("{}", List.of("name", "exercises")),
                Arguments.of(body("   ", one), List.of("name")),
                Arguments.of(body("a".repeat(101), one), List.of("name")),
                Arguments.of("{\"name\":\"A\",\"description\":\"" + "d".repeat(1001) + "\",\"exercises\":" + one + "}",
                        List.of("description")),
                Arguments.of(body("A", "[]"), List.of("exercises")),
                Arguments.of(body("A", "[" + (exercise("[" + SET + "]") + ",").repeat(30)
                        + exercise("[" + SET + "]") + "]"), List.of("exercises")),
                Arguments.of(body("A", "[" + exercise("[" + SET + "]") + ",null]"), List.of("exercises[1]")),
                Arguments.of(body("A", "[" + exercise("[" + SET + "]") + ",{\"sets\":[" + SET + "]}]"),
                        List.of("exercises[1].exerciseId")),
                Arguments.of(body("A", "[{\"exerciseId\":\"" + EXERCISE_ID + "\",\"note\":\"" + "n".repeat(501)
                        + "\",\"sets\":[" + SET + "]}]"), List.of("exercises[0].note")),
                Arguments.of(body("A", "[" + exercise("[]") + "]"), List.of("exercises[0].sets")),
                Arguments.of(body("A", "[" + exercise("[" + (SET + ",").repeat(20) + SET + "]") + "]"),
                        List.of("exercises[0].sets")),
                Arguments.of(body("A", "[" + exercise("[" + SET + ",null]") + "]"), List.of("exercises[0].sets[1]")),
                Arguments.of(body("A", "[" + exercise("[" + SET + "]") + "," + exercise("[" + SET
                                + ",{\"reps\":0,\"loadKg\":-0.5,\"restSeconds\":-1}]") + "]"),
                        List.of("exercises[1].sets[1].reps", "exercises[1].sets[1].loadKg",
                                "exercises[1].sets[1].restSeconds")),
                Arguments.of(body("A", "[" + exercise("[{\"reps\":101,\"loadKg\":1000,\"restSeconds\":601}]") + "]"),
                        List.of("exercises[0].sets[0].reps", "exercises[0].sets[0].loadKg",
                                "exercises[0].sets[0].restSeconds")),
                Arguments.of(body("A", "[" + exercise("[{\"loadKg\":60.25}]") + "]"),
                        List.of("exercises[0].sets[0].reps", "exercises[0].sets[0].loadKg")));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void refusesAnInvalidWorkoutWithThePathOfEachField(String body, List<String> fields) throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
                .andExpect(jsonPath("$.errors", hasSize(fields.size())))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(fields.toArray())));
        mockMvc.perform(put(BASE + "/" + WORKOUT_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED));
        verifyNoInteractions(workoutService);
    }

    @Test
    void theMessagesOfTheNestedErrorsAreTheOnesOfTheContract() throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("A", "[" + exercise("[{\"reps\":8,\"loadKg\":60.2}]") + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("exercises[0].sets[0].loadKg"))
                .andExpect(jsonPath("$.errors[0].message").value("La charge doit être un multiple de 0,5 kg"));
        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("A", "[]")))
                .andExpect(jsonPath("$.errors[0].message").value("Ajoutez au moins un exercice"));
    }

    @Test
    void anUnreadableBodyAnswersMalformedRequest() throws Exception {
        for (String body : List.of(
                body("A", "[{\"exerciseId\":\"pas-un-uuid\",\"sets\":[" + SET + "]}]"),
                body("A", "[" + exercise("[{\"reps\":8.5}]") + "]"),
                body("A", "[" + exercise("[{\"reps\":8,\"restSeconds\":90.5}]") + "]"),
                "{\"name\":")) {
            mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.MALFORMED_REQUEST));
        }
        verifyNoInteractions(workoutService);
    }

    @Test
    void aWorkoutIdThatIsNotAUuidAnswersInvalidParameter() throws Exception {
        mockMvc.perform(get(BASE + "/pas-un-uuid").with(as(Role.COACH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(post(BASE + "/pas-un-uuid/duplicate").with(as(Role.COACH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        verifyNoInteractions(workoutService);
    }

    @Test
    void duplicateNameAnswers409WithTheField() throws Exception {
        when(workoutService.create(any())).thenThrow(WorkoutErrors.workoutNameAlreadyExists());

        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKOUT_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.detail").value("Vous avez déjà une séance de ce nom"));
    }

    @Test
    void anExerciseOfAnotherCoachAnswers404ExerciseNotFound() throws Exception {
        when(workoutService.create(any())).thenThrow(ExerciseErrors.exerciseNotFound());
        when(workoutService.update(eq(WORKOUT_ID), any())).thenThrow(ExerciseErrors.exerciseNotFound());

        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
        mockMvc.perform(put(BASE + "/" + WORKOUT_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- détail, remplacement, duplication, suppression

    @Test
    void readsReplacesDuplicatesAndDeletesAWorkout() throws Exception {
        when(workoutService.get(WORKOUT_ID)).thenReturn(workout(WORKOUT_ID, "Haut du corps A"));
        when(workoutService.update(eq(WORKOUT_ID), any())).thenReturn(workout(WORKOUT_ID, "Haut du corps B"));
        when(workoutService.duplicate(WORKOUT_ID)).thenReturn(workout(COPY_ID, "Haut du corps A (copie)"));

        mockMvc.perform(get(BASE + "/" + WORKOUT_ID).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].sets", hasSize(2)))
                .andExpect(jsonPath("$.createdAt").value("2026-10-23T08:15:30Z"));
        mockMvc.perform(put(BASE + "/" + WORKOUT_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Haut du corps B"));
        mockMvc.perform(post(BASE + "/" + WORKOUT_ID + "/duplicate").with(as(Role.COACH)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/" + COPY_ID))
                .andExpect(jsonPath("$.name").value("Haut du corps A (copie)"));
        mockMvc.perform(delete(BASE + "/" + WORKOUT_ID).with(as(Role.COACH)))
                .andExpect(status().isNoContent());
        verify(workoutService).delete(WORKOUT_ID);
    }

    @Test
    void theWorkoutOfAnotherCoachAnswers404Everywhere() throws Exception {
        when(workoutService.get(WORKOUT_ID)).thenThrow(WorkoutErrors.workoutNotFound());
        when(workoutService.update(eq(WORKOUT_ID), any())).thenThrow(WorkoutErrors.workoutNotFound());
        when(workoutService.duplicate(WORKOUT_ID)).thenThrow(WorkoutErrors.workoutNotFound());
        doThrow(WorkoutErrors.workoutNotFound()).when(workoutService).delete(WORKOUT_ID);
        String one = BASE + "/" + WORKOUT_ID;

        mockMvc.perform(get(one).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Cette séance n'existe plus"));
        mockMvc.perform(put(one).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(post(one + "/duplicate").with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(delete(one).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
    }

    @Test
    void aCopyNameTakenMeanwhileAnswers409WithoutField() throws Exception {
        when(workoutService.duplicate(WORKOUT_ID)).thenThrow(WorkoutErrors.copyNameAlreadyExists());

        mockMvc.perform(post(BASE + "/" + WORKOUT_ID + "/duplicate").with(as(Role.COACH)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKOUT_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }
}
