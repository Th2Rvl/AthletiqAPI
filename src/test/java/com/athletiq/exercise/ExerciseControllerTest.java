package com.athletiq.exercise;

import static com.athletiq.support.TestJwt.as;
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
import com.athletiq.exercise.dto.CreateExerciseRequest;
import com.athletiq.exercise.dto.ExerciseResponse;
import com.athletiq.exercise.dto.UpdateExerciseRequest;
import com.athletiq.support.WebSecurityTestConfig;
import com.athletiq.user.Role;
import java.time.Instant;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExerciseController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class ExerciseControllerTest {

    private static final String BASE = "/api/v1/exercises";
    private static final UUID EXERCISE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120010");
    private static final Instant NOW = Instant.parse("2026-10-23T08:15:30Z");
    private static final String VALID_BODY = "{\"name\":\"Squat\",\"muscleGroup\":\"QUADRICEPS\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExerciseService exerciseService;

    private static ExerciseResponse squat() {
        return new ExerciseResponse(EXERCISE_ID, "Squat", "Pousser la barre", MuscleGroup.QUADRICEPS, "BARRE",
                "https://example.com/squat", NOW, NOW);
    }

    static Stream<Arguments> allEndpoints() {
        String one = BASE + "/" + EXERCISE_ID;
        return Stream.of(
                Arguments.of(HttpMethod.GET, BASE),
                Arguments.of(HttpMethod.POST, BASE),
                Arguments.of(HttpMethod.GET, one),
                Arguments.of(HttpMethod.PUT, one),
                Arguments.of(HttpMethod.DELETE, one));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allEndpoints")
    void refusesAnonymousAdminAndAthlete(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized());
        for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
            mockMvc.perform(request(method, uri).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
        }
        verifyNoInteractions(exerciseService);
    }

    @Test
    void listsWithTheSearchAndTheFiltersAndAPagedModel() throws Exception {
        when(exerciseService.list(eq("squ"), eq(MuscleGroup.QUADRICEPS), eq("BARRE"), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(squat())));

        mockMvc.perform(get(BASE).with(as(Role.COACH))
                        .param("q", "squ").param("muscleGroup", "QUADRICEPS").param("equipment", "BARRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Squat"))
                .andExpect(jsonPath("$.content[0].muscleGroup").value("QUADRICEPS"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void refusesAnUnknownMuscleGroupOrAnOverlongSearch() throws Exception {
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("muscleGroup", "INCONNU"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PARAMETER));
        verifyNoInteractions(exerciseService);
    }

    @Test
    void createsAnExerciseWithItsLocation() throws Exception {
        when(exerciseService.create(any())).thenReturn(squat());

        mockMvc.perform(post(BASE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/" + EXERCISE_ID))
                .andExpect(jsonPath("$.id").value(EXERCISE_ID.toString()));
        verify(exerciseService).create(new CreateExerciseRequest("Squat", null, MuscleGroup.QUADRICEPS, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\"\",\"muscleGroup\":\"DOS\"}",
            "{\"name\":\"   \",\"muscleGroup\":\"DOS\"}",
            "{\"name\":\"Tractions\"}",
            "{\"name\":\"Tractions\",\"muscleGroup\":\"DOS\",\"videoUrl\":\"pas une url\"}"})
    void refusesAnInvalidExercise(String body) throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(put(BASE + "/" + EXERCISE_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED));
        verifyNoInteractions(exerciseService);
    }

    @Test
    void refusesADescriptionOverOneThousandCharacters() throws Exception {
        String body = "{\"name\":\"Tractions\",\"muscleGroup\":\"DOS\",\"description\":\"" + "a".repeat(1001) + "\"}";

        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(exerciseService);
    }

    @Test
    void refusesAMuscleGroupOutsideTheFixedList() throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squat\",\"muscleGroup\":\"JAMBES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.MALFORMED_REQUEST));
        verifyNoInteractions(exerciseService);
    }

    @Test
    void duplicateNameAnswers409WithTheField() throws Exception {
        when(exerciseService.create(any())).thenThrow(ExerciseErrors.exerciseNameAlreadyExists());

        mockMvc.perform(post(BASE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCISE_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void readsReplacesAndDeletesAnExercise() throws Exception {
        when(exerciseService.get(EXERCISE_ID)).thenReturn(squat());
        when(exerciseService.update(EXERCISE_ID, new UpdateExerciseRequest("Squat", null, MuscleGroup.QUADRICEPS,
                null, null)))
                .thenReturn(new ExerciseResponse(EXERCISE_ID, "Squat", null, MuscleGroup.QUADRICEPS, null, null,
                        NOW, NOW));

        mockMvc.perform(get(BASE + "/" + EXERCISE_ID).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment").value("BARRE"));
        mockMvc.perform(put(BASE + "/" + EXERCISE_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Squat"));
        mockMvc.perform(delete(BASE + "/" + EXERCISE_ID).with(as(Role.COACH)))
                .andExpect(status().isNoContent());
        verify(exerciseService).delete(EXERCISE_ID);
    }

    @Test
    void theExerciseOfAnotherCoachAnswers404() throws Exception {
        when(exerciseService.get(EXERCISE_ID)).thenThrow(ExerciseErrors.exerciseNotFound());
        when(exerciseService.update(any(), any())).thenThrow(ExerciseErrors.exerciseNotFound());
        doThrow(ExerciseErrors.exerciseNotFound()).when(exerciseService).delete(EXERCISE_ID);

        mockMvc.perform(get(BASE + "/" + EXERCISE_ID).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
        mockMvc.perform(put(BASE + "/" + EXERCISE_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + EXERCISE_ID).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
    }

    @Test
    void anExerciseUsedInWorkoutsAnswers409WithTheWorkoutCount() throws Exception {
        doThrow(ExerciseErrors.exerciseInUse(3, 2)).when(exerciseService).delete(EXERCISE_ID);

        mockMvc.perform(delete(BASE + "/" + EXERCISE_ID).with(as(Role.COACH)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCISE_IN_USE"))
                .andExpect(jsonPath("$.workoutCount").value(3))
                .andExpect(jsonPath("$.plannedSessionCount").value(2))
                .andExpect(jsonPath("$.detail").value("Cet exercice est utilisé dans 3 séances et 2 séances planifiées"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }
}
