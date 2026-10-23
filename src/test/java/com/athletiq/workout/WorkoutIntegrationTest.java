package com.athletiq.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.TestcontainersConfiguration;
import com.athletiq.user.Role;
import com.athletiq.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Séances sur une vraie base MySQL 8.4 (Testcontainers) : migration V6 validée par Hibernate
 * ({@code ddl-auto=validate}), contraintes (unicité du nom et des positions, CHECK, CASCADE, RESTRICT),
 * puis parcours HTTP complet avec de vrais JWT : création, ordre, PUT qui remplace, duplication profonde,
 * suppression, propriété (404) et {@code EXERCISE_IN_USE}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WorkoutIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@athletiq.test";
    private static final String PASSWORD = "mot-de-passe-123";
    private static final String WORKOUTS = "/api/v1/workouts";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserService userService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String coach;
    private String other;
    private String squat;
    private String bench;
    private String row;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        userService.create("coach@club.fr", PASSWORD, "Nicolas", "Petit", Role.COACH);
        userService.create("claire.martin@club.fr", PASSWORD, "Claire", "Martin", Role.COACH);
        coach = login("coach@club.fr");
        other = login("claire.martin@club.fr");
        squat = createExercise(coach, "Squat", "QUADRICEPS");
        bench = createExercise(coach, "Développé couché", "PECTORAUX");
        row = createExercise(coach, "Rowing", "DOS");
    }

    /** Les séances d'abord : la clé RESTRICT bloquerait la suppression des exercices (et des comptes). */
    @AfterEach
    void cleanDatabase() {
        jdbc.update("delete from workout");
        jdbc.update("delete from exercise");
        jdbc.update("delete from app_user where email <> ?", ADMIN_EMAIL);
    }

    // ---------------------------------------------------------------- schéma et contraintes

    @Test
    void appliesMigrationV6() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version = '6'", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void theDatabaseGuardsPositionsBoundsAndTheRestrictOnExercises() throws Exception {
        String id = id(create(coach, "Haut du corps A", exercise(squat, set(8, "60", 90))));
        byte[] workoutId = bytes(id);
        byte[] exerciseId = bytes(squat);

        // Position déjà prise dans la séance.
        assertThatThrownBy(() -> jdbc.update("insert into workout_exercise (id, workout_id, exercise_id, position, "
                + "created_at, updated_at) values (UUID_TO_BIN(UUID(), 1), ?, ?, 0, now(6), now(6))",
                workoutId, exerciseId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_workout_exercise_position");
        // Position hors bornes (au plus 30 exercices).
        assertThatThrownBy(() -> jdbc.update("insert into workout_exercise (id, workout_id, exercise_id, position, "
                + "created_at, updated_at) values (UUID_TO_BIN(UUID(), 1), ?, ?, 30, now(6), now(6))",
                workoutId, exerciseId))
                .isInstanceOf(DataAccessException.class);
        byte[] workoutExerciseId = jdbc.queryForObject("select id from workout_exercise where workout_id = ?",
                byte[].class, (Object) workoutId);
        for (String values : List.of("1, 0, 60.0, 90", "1, 101, 60.0, 90", "1, 8, 60.2, 90", "1, 8, -0.5, 90",
                "1, 8, 60.0, 601", "0, 8, 60.0, 90")) {
            assertThatThrownBy(() -> jdbc.update("insert into planned_set (id, workout_exercise_id, position, reps, "
                    + "load_kg, rest_seconds, created_at, updated_at) values (UUID_TO_BIN(UUID(), 1), ?, "
                    + values + ", now(6), now(6))", (Object) workoutExerciseId))
                    .as(values)
                    .isInstanceOf(DataAccessException.class);
        }
        // Un exercice utilisé ne peut pas être supprimé, même directement en base.
        assertThatThrownBy(() -> jdbc.update("delete from exercise where id = ?", (Object) exerciseId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_workout_exercise_exercise");
    }

    @Test
    void deletingAWorkoutCascadesToItsExercisesAndSetsButNotToTheLibrary() throws Exception {
        String id = id(create(coach, "Haut du corps A", exercise(squat, set(8, "60", 90), set(8, "60", 90)),
                exercise(bench, set(10, null, null))));
        assertThat(count("workout_exercise")).isEqualTo(2);
        assertThat(count("planned_set")).isEqualTo(3);

        mockMvc.perform(delete(WORKOUTS + "/" + id).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNoContent());

        assertThat(count("workout")).isZero();
        assertThat(count("workout_exercise")).isZero();
        assertThat(count("planned_set")).isZero();
        assertThat(count("exercise")).isEqualTo(3);
        mockMvc.perform(get(WORKOUTS + "/" + id).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(delete(WORKOUTS + "/" + id).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- création, ordre, nom

    @Test
    void createsAWorkoutAndReadsItBackInTheOrderSentWithoutNPlusOne() throws Exception {
        String sent = body("Haut du corps A", "  Consignes  ", noted(squat, "Tempo 3-1-1",
                        set(8, "60", 90), set(6, "62.5", 120), set(4, "65", null)),
                noted(bench, null, set(10, null, 60)),
                noted(squat, "Finisher", set(20, "40", null)));

        String created = mockMvc.perform(json(post(WORKOUTS), coach, sent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Consignes"))
                .andExpect(jsonPath("$.exercises[0].sets[0].loadKg").value(60.0))
                .andReturn().getResponse().getContentAsString();
        String id = id(created);
        assertThat(JsonPath.<String>read(created, "$.updatedAt")).isNotNull();

        Statistics statistics = statistics();
        try {
            mockMvc.perform(get(WORKOUTS + "/" + id).header(HttpHeaders.AUTHORIZATION, coach))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Haut du corps A"))
                    .andExpect(jsonPath("$.exercises[*].position", contains(0, 1, 2)))
                    .andExpect(jsonPath("$.exercises[*].exercise.name",
                            contains("Squat", "Développé couché", "Squat")))
                    .andExpect(jsonPath("$.exercises[0].note").value("Tempo 3-1-1"))
                    .andExpect(jsonPath("$.exercises[0].exercise.muscleGroup").value("QUADRICEPS"))
                    .andExpect(jsonPath("$.exercises[0].sets[*].position", contains(0, 1, 2)))
                    .andExpect(jsonPath("$.exercises[0].sets[*].reps", contains(8, 6, 4)))
                    .andExpect(jsonPath("$.exercises[0].sets[1].loadKg").value(62.5))
                    .andExpect(jsonPath("$.exercises[0].sets[2].restSeconds").isEmpty())
                    .andExpect(jsonPath("$.exercises[1].sets[0].loadKg").isEmpty())
                    .andExpect(jsonPath("$.exercises[1].note").isEmpty())
                    .andExpect(jsonPath("$.exercises[2].note").value("Finisher"));
            // Propriété (1) + séance, exercices et bibliothèque (1) + séries (1), quel que soit leur nombre.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void theNameIsUniquePerCoachIgnoringCaseAndAccentsButNotAcrossCoaches() throws Exception {
        create(coach, "Haut du corps A", exercise(squat, set(8, "60", 90)));

        for (String duplicate : List.of("haut du corps a", "HAUT DU CORPS Á", " Haut du corps A ")) {
            mockMvc.perform(json(post(WORKOUTS), coach, body(duplicate, null, exercise(squat, set(8, "60", 90)))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("WORKOUT_NAME_ALREADY_EXISTS"))
                    .andExpect(jsonPath("$.field").value("name"));
        }
        String othersSquat = createExercise(other, "Squat", "QUADRICEPS");
        create(other, "Haut du corps A", exercise(othersSquat, set(5, "100", 180)));
        assertThat(count("workout")).isEqualTo(2);
    }

    @Test
    void anExerciseOfAnotherCoachOrUnknownRefusesTheCreationAndWritesNothing() throws Exception {
        String othersSquat = createExercise(other, "Squat", "QUADRICEPS");

        for (String foreign : List.of(othersSquat, UUID.randomUUID().toString())) {
            mockMvc.perform(json(post(WORKOUTS), coach, body("Haut du corps A", null,
                            exercise(squat, set(8, "60", 90)), exercise(foreign, set(8, "60", 90)))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
        }
        assertThat(count("workout")).isZero();
        assertThat(count("workout_exercise")).isZero();
    }

    // ---------------------------------------------------------------- PUT qui remplace

    @Test
    void putReplacesTheWholeWorkoutAndReordersItsExercises() throws Exception {
        String id = id(create(coach, "Haut du corps A",
                exercise(squat, set(8, "60", 90)),
                exercise(bench, set(10, "40", 60), set(10, "40", 60)),
                exercise(row, set(12, "50", 60))));
        Instant createdAt = Instant.parse(JsonPath.read(readWorkout(id), "$.createdAt"));
        Instant before = Instant.parse(JsonPath.read(readWorkout(id), "$.updatedAt"));

        // C, A, B : le rowing passe en tête, avec les mêmes séries ; la séance garde son nom.
        mockMvc.perform(json(put(WORKOUTS + "/" + id), coach, body("Haut du corps A", null,
                        exercise(row, set(12, "50", 60)),
                        exercise(squat, set(8, "60", 90)),
                        exercise(bench, set(10, "40", 60), set(10, "40", 60)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[*].exercise.name", contains("Rowing", "Squat", "Développé couché")));

        String read = readWorkout(id);
        assertThat(JsonPath.<List<String>>read(read, "$.exercises[*].exercise.name"))
                .containsExactly("Rowing", "Squat", "Développé couché");
        assertThat(JsonPath.<List<Integer>>read(read, "$.exercises[*].position")).containsExactly(0, 1, 2);
        assertThat(JsonPath.<List<Integer>>read(read, "$.exercises[2].sets[*].reps")).containsExactly(10, 10);
        assertThat(count("workout_exercise")).isEqualTo(3);
        assertThat(count("planned_set")).isEqualTo(4);

        // Seules les séries changent : la séance est tout de même datée de cette modification.
        mockMvc.perform(json(put(WORKOUTS + "/" + id), coach, body("Haut du corps A", null,
                        exercise(row, set(12, "52.5", 60)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises", hasSize(1)))
                .andExpect(jsonPath("$.exercises[0].sets[0].loadKg").value(52.5));
        read = readWorkout(id);
        assertThat(Instant.parse(JsonPath.read(read, "$.updatedAt"))).isAfter(before);
        assertThat(Instant.parse(JsonPath.read(read, "$.createdAt"))).isEqualTo(createdAt);
        assertThat(count("workout_exercise")).isEqualTo(1);
        assertThat(count("planned_set")).isEqualTo(1);
    }

    @Test
    void aRefusedPutChangesNothing() throws Exception {
        String id = id(create(coach, "Haut du corps A", exercise(squat, set(8, "60", 90))));
        create(coach, "Bas du corps", exercise(squat, set(5, "100", 180)));
        String othersSquat = createExercise(other, "Squat", "QUADRICEPS");
        String before = readWorkout(id);

        mockMvc.perform(json(put(WORKOUTS + "/" + id), coach, body("bas du corps", null,
                        exercise(bench, set(10, null, null)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKOUT_NAME_ALREADY_EXISTS"));
        mockMvc.perform(json(put(WORKOUTS + "/" + id), coach, body("Haut du corps B", null,
                        exercise(bench, set(10, null, null)), exercise(othersSquat, set(1, null, null)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));

        assertThat(readWorkout(id)).isEqualTo(before);
    }

    // ---------------------------------------------------------------- duplication

    @Test
    void duplicatesDeeplyAndTheCopyIsIndependentOfTheOriginal() throws Exception {
        String id = id(create(coach, "Haut du corps A", noted(squat, "Tempo", set(8, "60", 90), set(6, "62.5", null)),
                exercise(bench, set(10, null, 60))));
        String original = readWorkout(id);

        String copy = mockMvc.perform(post(WORKOUTS + "/" + id + "/duplicate").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Haut du corps A (copie)"))
                .andReturn().getResponse().getContentAsString();
        String copyId = id(copy);
        assertThat(copyId).isNotEqualTo(id);
        assertThat(JsonPath.<Object>read(copy, "$.exercises")).isEqualTo(JsonPath.read(original, "$.exercises"));

        mockMvc.perform(post(WORKOUTS + "/" + id + "/duplicate").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.name").value("Haut du corps A (copie 2)"));
        mockMvc.perform(post(WORKOUTS + "/" + copyId + "/duplicate").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Haut du corps A (copie) (copie)"));

        // Modifier puis supprimer la copie ne touche pas l'original.
        mockMvc.perform(json(put(WORKOUTS + "/" + copyId), coach, body("Haut du corps A (copie)", null,
                        exercise(row, set(1, null, null)))))
                .andExpect(status().isOk());
        assertThat(readWorkout(id)).isEqualTo(original);
        mockMvc.perform(delete(WORKOUTS + "/" + copyId).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNoContent());
        assertThat(readWorkout(id)).isEqualTo(original);
    }

    // ---------------------------------------------------------------- liste

    @Test
    void listsOnlyTheWorkoutsOfTheCoachMostRecentlyModifiedFirstWithTheirSummary() throws Exception {
        String first = id(create(coach, "Haut du corps A", exercise(squat, set(8, "60", 90)),
                exercise(bench, set(10, null, null)), exercise(squat, set(5, "80", null))));
        create(coach, "Dos", exercise(row, set(12, "50", 60)));
        create(coach, "Jambes 100% lourdes", exercise(squat, set(3, "120", 240)));
        String othersSquat = createExercise(other, "Squat", "QUADRICEPS");
        create(other, "Haut du corps B", exercise(othersSquat, set(5, "100", 180)));
        // Remplacer « Haut du corps A » le fait remonter en tête.
        mockMvc.perform(json(put(WORKOUTS + "/" + first), coach, body("Haut du corps A", null,
                        exercise(squat, set(8, "60", 90)), exercise(bench, set(10, null, null)),
                        exercise(squat, set(5, "80", null)))))
                .andExpect(status().isOk());

        Statistics statistics = statistics();
        try {
            mockMvc.perform(get(WORKOUTS).header(HttpHeaders.AUTHORIZATION, coach))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].name", contains("Haut du corps A", "Jambes 100% lourdes", "Dos")))
                    .andExpect(jsonPath("$.content[0].exerciseCount").value(3))
                    .andExpect(jsonPath("$.content[0].muscleGroups", contains("PECTORAUX", "QUADRICEPS")))
                    .andExpect(jsonPath("$.content[2].muscleGroups", contains("DOS")))
                    .andExpect(jsonPath("$.page.totalElements").value(3));
            // La page puis l'agrégation des groupes musculaires, sans requête par séance.
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(false);
        }

        mockMvc.perform(get(WORKOUTS).param("q", "HAUT").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(jsonPath("$.content[*].name", contains("Haut du corps A")));
        mockMvc.perform(get(WORKOUTS).param("q", "100%").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(jsonPath("$.content[*].name", contains("Jambes 100% lourdes")));
        mockMvc.perform(get(WORKOUTS).param("q", "%").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(jsonPath("$.content[*].name", contains("Jambes 100% lourdes")));
        mockMvc.perform(get(WORKOUTS).param("size", "2").param("page", "1").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(jsonPath("$.content[*].name", contains("Dos")))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        mockMvc.perform(get(WORKOUTS).param("q", "introuvable").header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ---------------------------------------------------------------- propriété

    @Test
    void anotherCoachGets404EverywhereAndChangesNothing() throws Exception {
        String id = id(create(coach, "Haut du corps A", exercise(squat, set(8, "60", 90))));
        String othersSquat = createExercise(other, "Squat", "QUADRICEPS");
        String before = readWorkout(id);
        String one = WORKOUTS + "/" + id;

        mockMvc.perform(get(one).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(json(put(one), other, body("Piratée", null, exercise(othersSquat, set(1, null, null)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(post(one + "/duplicate").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(delete(one).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(get(WORKOUTS).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.content", hasSize(0)));

        assertThat(readWorkout(id)).isEqualTo(before);
        assertThat(count("workout")).isEqualTo(1);
    }

    @Test
    void anAthleteAndAnAdminAreRefused() throws Exception {
        userService.create("lucas.girard@athlete.fr", PASSWORD, "Lucas", "Girard", Role.ATHLETE);
        for (String token : List.of(login("lucas.girard@athlete.fr"), login(ADMIN_EMAIL))) {
            mockMvc.perform(get(WORKOUTS).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        mockMvc.perform(get(WORKOUTS)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- EXERCISE_IN_USE (reprise de #4)

    @Test
    void anExerciseUsedInWorkoutsCannotBeDeletedUntilTheyAreGone() throws Exception {
        String first = id(create(coach, "Haut du corps A", exercise(squat, set(8, "60", 90)),
                exercise(squat, set(5, "80", null))));
        String second = id(create(coach, "Jambes", exercise(squat, set(3, "120", 240))));

        mockMvc.perform(delete("/api/v1/exercises/" + squat).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCISE_IN_USE"))
                .andExpect(jsonPath("$.workoutCount").value(2))
                .andExpect(jsonPath("$.detail").value("Cet exercice est utilisé dans 2 séances"));
        assertThat(count("exercise")).isEqualTo(3);

        mockMvc.perform(delete(WORKOUTS + "/" + first).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/exercises/" + squat).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.workoutCount").value(1));

        mockMvc.perform(delete(WORKOUTS + "/" + second).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/exercises/" + squat).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNoContent());
        // Un exercice qui n'est dans aucune séance se supprime toujours.
        mockMvc.perform(delete("/api/v1/exercises/" + row).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isNoContent());
        assertThat(count("exercise")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- outils

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    private static String set(int reps, String loadKg, Integer restSeconds) {
        return "{\"reps\":" + reps + ",\"loadKg\":" + loadKg + ",\"restSeconds\":" + restSeconds + "}";
    }

    private static String exercise(String exerciseId, String... sets) {
        return noted(exerciseId, null, sets);
    }

    private static String noted(String exerciseId, String note, String... sets) {
        return "{\"exerciseId\":\"" + exerciseId + "\",\"note\":" + (note == null ? "null" : "\"" + note + "\"")
                + ",\"sets\":[" + String.join(",", sets) + "]}";
    }

    private static String body(String name, String description, String... exercises) {
        return "{\"name\":\"" + name + "\",\"description\":"
                + (description == null ? "null" : "\"" + description + "\"")
                + ",\"exercises\":[" + String.join(",", exercises) + "]}";
    }

    private String create(String token, String name, String... exercises) throws Exception {
        return mockMvc.perform(json(post(WORKOUTS), token, body(name, null, exercises)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String readWorkout(String id) throws Exception {
        return mockMvc.perform(get(WORKOUTS + "/" + id).header(HttpHeaders.AUTHORIZATION, coach))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String createExercise(String token, String name, String muscleGroup) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/exercises"), token,
                        "{\"name\":\"" + name + "\",\"muscleGroup\":\"" + muscleGroup + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/auth/login"), null,
                        "{\"email\":\"" + email + "\",\"password\":\""
                                + (ADMIN_EMAIL.equals(email) ? "test-admin-password" : PASSWORD) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private static String id(String body) {
        return JsonPath.read(body, "$.id");
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static byte[] bytes(String uuid) {
        UUID id = UUID.fromString(uuid);
        return java.nio.ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token,
                                                      String body) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, token);
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
