package com.athletiq.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.TestcontainersConfiguration;
import com.athletiq.athlete.CoachAthlete;
import com.athletiq.athlete.CoachAthleteRepository;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import com.jayway.jsonpath.JsonPath;
import java.nio.ByteBuffer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Planification sur une vraie base MySQL 8.4 (Testcontainers) : migration V7 validée par Hibernate, contraintes
 * (pas d'UNIQUE par jour, RESTRICT exercice et instantané, SET NULL modèle et groupe), puis parcours HTTP avec
 * de vrais JWT : instantané insensible au modèle, groupe à instantané partagé, n/N, duplication, annulation et
 * orphelins, propriété (404), multi-coach sans fuite, P7 et {@code EXERCISE_IN_USE} à deux compteurs.
 * « Aujourd'hui » est celui de l'application ({@link PlanningClock}) : les dates sont relatives.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlanningIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@athletiq.test";
    private static final String PASSWORD = "mot-de-passe-123";
    private static final String SESSIONS = "/api/v1/planned-sessions/";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserService userService;
    @Autowired
    private CoachAthleteRepository linkRepository;
    @Autowired
    private PlanningClock planningClock;

    private LocalDate today;
    private User coach;
    private User claire;
    private User lucas;
    private User emma;
    private User hugo;
    private String coachToken;
    private String claireToken;
    private String squat;
    private String bench;
    private String workout;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        today = planningClock.today();
        coach = userService.create("coach@club.fr", PASSWORD, "Nicolas", "Petit", Role.COACH);
        claire = userService.create("claire.martin@club.fr", PASSWORD, "Claire", "Martin", Role.COACH);
        lucas = userService.create("lucas.girard@athlete.fr", PASSWORD, "Lucas", "Girard", Role.ATHLETE);
        emma = userService.create("emma.lefevre@athlete.fr", PASSWORD, "Emma", "Lefèvre", Role.ATHLETE);
        hugo = userService.create("hugo.fontaine@athlete.fr", PASSWORD, "Hugo", "Fontaine", Role.ATHLETE);
        for (User athlete : List.of(lucas, emma, hugo)) {
            follow(coach, athlete);
        }
        follow(claire, lucas);
        coachToken = login("coach@club.fr");
        claireToken = login("claire.martin@club.fr");
        squat = createExercise(coachToken, "Squat", "QUADRICEPS");
        bench = createExercise(coachToken, "Développé couché", "PECTORAUX");
        workout = createWorkout(coachToken, "Haut du corps A", bench, squat);
    }

    /** Séances planifiées d'abord : la clé RESTRICT bloquerait la suppression des exercices. */
    @AfterEach
    void cleanDatabase() {
        jdbc.update("delete from planned_session");
        jdbc.update("delete from session_snapshot");
        jdbc.update("delete from workout");
        jdbc.update("delete from exercise");
        jdbc.update("delete from athlete_group_member");
        jdbc.update("delete from athlete_group");
        jdbc.update("delete from coach_athlete");
        jdbc.update("delete from app_user where email <> ?", ADMIN_EMAIL);
    }

    // ---------------------------------------------------------------- schéma et contraintes

    @Test
    void appliesMigrationV7WithoutAUniqueDayPerAthlete() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version = '7'", Integer.class);
        assertThat(applied).isEqualTo(1);
        Integer uniques = jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = database() and table_name = 'planned_session' and constraint_type = 'UNIQUE'
                """, Integer.class);
        assertThat(uniques).isZero();
    }

    @Test
    void theDatabaseRestrictsExercisesAndSnapshotsStillReferenced() throws Exception {
        String id = id(planForLucas(today.plusDays(1)));
        byte[] snapshotId = jdbc.queryForObject("select snapshot_id from planned_session where id = ?",
                byte[].class, (Object) bytes(id));

        // L'exercice reste protégé même après la suppression du modèle (Q11).
        jdbc.update("delete from workout");
        assertThatThrownBy(() -> jdbc.update("delete from exercise where id = ?", (Object) bytes(squat)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_session_snapshot_exercise_exercise");
        assertThatThrownBy(() -> jdbc.update("delete from session_snapshot where id = ?", (Object) snapshotId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_planned_session_snapshot");
        // Mêmes bornes que les séries d'une séance.
        byte[] snapshotExerciseId = jdbc.queryForObject(
                "select id from session_snapshot_exercise where snapshot_id = ? and position = 0",
                byte[].class, (Object) snapshotId);
        // Position, répétitions, charge, repos : chaque ligne viole un CHECK (ou l'unicité de la position 0).
        for (String values : List.of("5, 0, 60.0, 90", "5, 8, 60.2, 90", "5, 8, 60.0, 601", "20, 8, 60.0, 90",
                "0, 8, 60.0, 90")) {
            assertThatThrownBy(() -> jdbc.update("insert into session_snapshot_set (id, snapshot_exercise_id, "
                    + "position, reps, load_kg, rest_seconds, created_at, updated_at) values (UUID_TO_BIN(UUID(), 1), ?, "
                    + values + ", now(6), now(6))", (Object) snapshotExerciseId))
                    .as(values)
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void deletingTheWorkoutOrTheGroupSetsTheirColumnsToNullAndKeepsTheContent() throws Exception {
        String group = createGroup("Sprint", lucas, emma);
        String individual = id(planForLucas(today.plusDays(1)));
        planForGroup(group, today.plusDays(2), 2);
        String before = detail(individual);

        mockMvc.perform(delete("/api/v1/workouts/" + workout).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/groups/" + group).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());

        assertThat(count("planned_session")).isEqualTo(3);
        assertThat(count("planned_session where source_workout_id is not null")).isZero();
        assertThat(count("planned_session where group_id is not null")).isZero();
        String after = detail(individual);
        assertThat(JsonPath.<String>read(after, "$.sourceWorkoutId")).isNull();
        assertThat(JsonPath.<Object>read(after, "$.exercises")).isEqualTo(JsonPath.read(before, "$.exercises"));
        assertThat(JsonPath.<String>read(after, "$.name")).isEqualTo("Haut du corps A");
    }

    // ---------------------------------------------------------------- US21, US25 athlète

    @Test
    void aPlannedSessionKeepsItsSnapshotWhenTheWorkoutChanges() throws Exception {
        String created = planForLucas(today);
        String id = id(created);
        assertThat(JsonPath.<String>read(created, "$.date")).isEqualTo(today.toString());
        assertThat(JsonPath.<List<String>>read(created, "$.exercises[*].exercise.name"))
                .containsExactly("Développé couché", "Squat");

        mockMvc.perform(json(put("/api/v1/workouts/" + workout), coachToken, workoutBody("Haut du corps B", squat)))
                .andExpect(status().isOk());
        mockMvc.perform(json(put("/api/v1/exercises/" + bench), coachToken,
                        "{\"name\":\"Développé incliné\",\"muscleGroup\":\"PECTORAUX\"}"))
                .andExpect(status().isOk());

        String read = detail(id);
        assertThat(JsonPath.<String>read(read, "$.name")).isEqualTo("Haut du corps A");
        assertThat(JsonPath.<List<String>>read(read, "$.exercises[*].exercise.name"))
                .containsExactly("Développé couché", "Squat");
        assertThat(JsonPath.<List<Integer>>read(read, "$.exercises[0].sets[*].reps")).containsExactly(8, 6);
        assertThat(JsonPath.<String>read(read, "$.exercises[0].note")).isEqualTo("Tempo 3-1-1");
        assertThat(JsonPath.<String>read(read, "$.athleteFirstName")).isEqualTo("Lucas");
    }

    @Test
    void severalSessionsTheSameDayAreAcceptedEvenConcurrently() throws Exception {
        LocalDate day = today.plusDays(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(json(post(athleteSessions(lucas)), coachToken, planBody(day)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }
        planForLucas(day);

        mockMvc.perform(get(athleteSessions(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(3)))
                .andExpect(jsonPath("$.sessions[0].exerciseCount").value(2))
                .andExpect(jsonPath("$.sessions[0].setCount").value(3));
        assertThat(count("session_snapshot")).isEqualTo(3);
    }

    @Test
    void refusalsCreateNothing() throws Exception {
        User stranger = userService.create("nathan@athlete.fr", PASSWORD, "Nathan", "Chevalier", Role.ATHLETE);
        String foreignWorkout = createWorkout(claireToken, "Séance de Claire",
                createExercise(claireToken, "Tirage", "DOS"));

        mockMvc.perform(json(post(athleteSessions(stranger)), coachToken, planBody(today)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));
        mockMvc.perform(json(post(athleteSessions(lucas)), coachToken,
                        "{\"workoutId\":\"" + foreignWorkout + "\",\"date\":\"" + today + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKOUT_NOT_FOUND"));
        mockMvc.perform(json(post(athleteSessions(lucas)), coachToken, planBody(today.minusDays(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANNING_DATE_IN_PAST"))
                .andExpect(jsonPath("$.field").value("date"));
        assertThat(count("planned_session")).isZero();
        assertThat(count("session_snapshot")).isZero();
    }

    @Test
    void movingAndCancellingRespectTheLockAndCleanTheOrphanSnapshot() throws Exception {
        String id = id(planForLucas(today.plusDays(1)));

        mockMvc.perform(json(patch(SESSIONS + id), coachToken, "{\"date\":\"" + today.plusDays(5) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.plusDays(5).toString()))
                .andExpect(jsonPath("$.name").value("Haut du corps A"));
        mockMvc.perform(json(patch(SESSIONS + id), coachToken, "{\"date\":\"" + today.minusDays(1) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANNING_DATE_IN_PAST"));
        // Une autre personne ne voit rien : 404, jamais 403.
        mockMvc.perform(get(SESSIONS + id).header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_NOT_FOUND"));
        mockMvc.perform(delete(SESSIONS + id).header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(SESSIONS + id).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(SESSIONS + id).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNotFound());
        assertThat(count("planned_session")).isZero();
        assertThat(count("session_snapshot")).isZero();
        assertThat(count("session_snapshot_exercise")).isZero();

        // Une séance passée (écrite en base : l'API refuse le passé) est verrouillée.
        String past = id(planForLucas(today));
        jdbc.update("update planned_session set scheduled_date = ? where id = ?", today.minusDays(2), bytes(past));
        mockMvc.perform(get(SESSIONS + past).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));
        mockMvc.perform(json(patch(SESSIONS + past), coachToken, "{\"date\":\"" + today.plusDays(1) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_LOCKED"));
        mockMvc.perform(delete(SESSIONS + past).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_LOCKED"));
    }

    // ---------------------------------------------------------------- US22, US24 groupe

    @Test
    void aGroupPlanningSharesOneSnapshotAndTheCalendarShowsNOverNForCurrentMembers() throws Exception {
        String group = createGroup("Sprint", lucas, emma);
        LocalDate day = today.plusDays(2);
        planForGroup(group, day, 2);
        assertThat(count("session_snapshot")).isEqualTo(1);
        assertThat(count("planned_session where group_id is not null")).isEqualTo(2);

        // Un membre ajouté ensuite ne reçoit rien ; il compte dans N.
        mockMvc.perform(json(post("/api/v1/groups/" + group + "/athletes"), coachToken,
                        "{\"athleteIds\":[\"" + hugo.getId() + "\"]}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/v1/groups/" + group + "/planned-sessions").header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", today.toString()).param("to", today.plusDays(27).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(3))
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].name").value("Haut du corps A"))
                .andExpect(jsonPath("$.entries[0].athleteCount").value(2))
                .andExpect(jsonPath("$.entries[0].memberCount").value(3))
                .andExpect(jsonPath("$.entries[0].sessions[0].lastName").value("Girard"))
                .andExpect(jsonPath("$.entries[0].sessions[1].lastName").value("Lefèvre"));

        // Annuler la séance d'un membre garde l'instantané partagé par l'autre.
        String calendar = athleteCalendar(coachToken, emma, day, day);
        String emmaSession = JsonPath.read(calendar, "$.sessions[0].id");
        mockMvc.perform(delete(SESSIONS + emmaSession).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        assertThat(count("session_snapshot")).isEqualTo(1);

        // Un membre retiré du groupe garde sa séance, mais n'apparaît plus dans la vue groupe.
        mockMvc.perform(delete("/api/v1/groups/" + group + "/athletes/" + lucas.getId())
                        .header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        assertThat(JsonPath.<List<Object>>read(athleteCalendar(coachToken, lucas, day, day), "$.sessions")).hasSize(1);
        mockMvc.perform(get("/api/v1/groups/" + group + "/planned-sessions").header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", today.toString()).param("to", today.plusDays(27).toString()))
                .andExpect(jsonPath("$.entries", hasSize(0)));

        // Groupe d'autrui et groupe vide.
        mockMvc.perform(json(post("/api/v1/groups/" + group + "/planned-sessions"), claireToken, planBody(day)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        String empty = createGroup("Vide");
        mockMvc.perform(json(post("/api/v1/groups/" + empty + "/planned-sessions"), coachToken, planBody(day)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_HAS_NO_MEMBERS"));
    }

    @Test
    void theCalendarOfAnotherCoachNeverLeaksAndThePeriodIsBounded() throws Exception {
        LocalDate day = today.plusDays(1);
        planForLucas(day);
        String claireWorkout = createWorkout(claireToken, "Séance de Claire",
                createExercise(claireToken, "Tirage", "DOS"));
        mockMvc.perform(json(post(athleteSessions(lucas)), claireToken,
                        "{\"workoutId\":\"" + claireWorkout + "\",\"date\":\"" + day + "\"}"))
                .andExpect(status().isCreated());

        assertThat(JsonPath.<List<String>>read(athleteCalendar(coachToken, lucas, day, day), "$.sessions[*].name"))
                .containsExactly("Haut du corps A");
        assertThat(JsonPath.<List<String>>read(athleteCalendar(claireToken, lucas, day, day), "$.sessions[*].name"))
                .containsExactly("Séance de Claire");
        mockMvc.perform(get(athleteSessions(emma)).header(HttpHeaders.AUTHORIZATION, claireToken)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));
        mockMvc.perform(get(athleteSessions(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", today.toString()).param("to", today.plusDays(84).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get(athleteSessions(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", today.toString()).param("to", today.plusDays(83).toString()))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- US26 duplication

    @Test
    void duplicatingAWeekReusesTheSnapshotsOnTheFollowingWeeks() throws Exception {
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        planForLucas(nextMonday.plusDays(2));
        planForLucas(nextMonday.plusDays(2));
        String group = createGroup("Sprint", lucas, emma);
        planForGroup(group, nextMonday.plusDays(4), 2);
        assertThat(count("session_snapshot")).isEqualTo(3);

        mockMvc.perform(json(post(athleteSessions(lucas) + "/duplicate-week"), coachToken,
                        "{\"weekStart\":\"" + nextMonday + "\",\"weeks\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.created").value(6));
        mockMvc.perform(json(post("/api/v1/groups/" + group + "/planned-sessions/duplicate-week"), coachToken,
                        "{\"weekStart\":\"" + nextMonday + "\",\"weeks\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2));

        assertThat(count("session_snapshot")).isEqualTo(3);
        assertThat(count("planned_session")).isEqualTo(4 + 6 + 2);
        assertThat(JsonPath.<List<Object>>read(athleteCalendar(coachToken, lucas, nextMonday.plusWeeks(2),
                nextMonday.plusWeeks(2).plusDays(6)), "$.sessions")).hasSize(3);

        LocalDate empty = nextMonday.plusWeeks(6);
        mockMvc.perform(json(post(athleteSessions(lucas) + "/duplicate-week"), coachToken,
                        "{\"weekStart\":\"" + empty + "\",\"weeks\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANNING_WEEK_EMPTY"));
        mockMvc.perform(json(post(athleteSessions(lucas) + "/duplicate-week"), coachToken,
                        "{\"weekStart\":\"" + nextMonday.plusDays(1) + "\",\"weeks\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
    }

    // ---------------------------------------------------------------- reprises

    @Test
    void anExercisePlannedEvenAfterItsWorkoutIsDeletedCannotBeDeleted() throws Exception {
        planForLucas(today.plusDays(1));
        mockMvc.perform(delete("/api/v1/workouts/" + workout).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/exercises/" + squat).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXERCISE_IN_USE"))
                .andExpect(jsonPath("$.workoutCount").value(0))
                .andExpect(jsonPath("$.plannedSessionCount").value(1))
                .andExpect(jsonPath("$.detail").value("Cet exercice est utilisé dans 1 séance planifiée"));
    }

    @Test
    void unfollowingAnAthleteDeletesOnlyTheUpcomingSessionsOfThisCoach() throws Exception {
        String past = id(planForLucas(today));
        jdbc.update("update planned_session set scheduled_date = ? where id = ?", today.minusDays(3), bytes(past));
        planForLucas(today);
        planForLucas(today.plusDays(7));
        String claireWorkout = createWorkout(claireToken, "Séance de Claire",
                createExercise(claireToken, "Tirage", "DOS"));
        mockMvc.perform(json(post(athleteSessions(lucas)), claireToken,
                        "{\"workoutId\":\"" + claireWorkout + "\",\"date\":\"" + today.plusDays(1) + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(athleteSessions(lucas) + "/upcoming-count").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(delete("/api/v1/athletes/" + lucas.getId()).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());

        assertThat(count("planned_session where coach_id = UUID_TO_BIN('" + coach.getId() + "')")).isEqualTo(1);
        assertThat(count("planned_session where coach_id = UUID_TO_BIN('" + claire.getId() + "')")).isEqualTo(1);
        // Deux instantanés orphelins supprimés : celui du passé et celui de Claire restent.
        assertThat(count("session_snapshot")).isEqualTo(2);
        // Lien clos : la séance passée conservée n'est plus accessible au coach.
        mockMvc.perform(get(SESSIONS + past).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_NOT_FOUND"));
        mockMvc.perform(json(post(athleteSessions(lucas)), coachToken, planBody(today)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));
    }

    @Test
    void anAthleteReadsOnlyHisOwnSessionsAndOnlyWhileFollowedByACoach() throws Exception {
        String sessionId = id(planForLucas(today));
        planForLucas(today.plusDays(2));
        String pastId = id(planForLucas(today));
        jdbc.update("update planned_session set scheduled_date = ? where id = ?", today.minusDays(2), bytes(pastId));
        String lucasToken = login("lucas.girard@athlete.fr");
        String emmaToken = login("emma.lefevre@athlete.fr");

        mockMvc.perform(get("/api/v1/me/planned-sessions/today").header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].id").value(sessionId))
                .andExpect(jsonPath("$.sessions[0].exercises.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].coachLastName").value("Petit"))
                .andExpect(jsonPath("$.sessions[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$.next.date").value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.next.exerciseNames.length()").value(2))
                .andExpect(jsonPath("$.last").doesNotExist());

        mockMvc.perform(get("/api/v1/me/planned-sessions").header(HttpHeaders.AUTHORIZATION, lucasToken)
                        .param("from", today.minusDays(7).toString()).param("to", today.plusDays(7).toString()))
                .andExpect(jsonPath("$.sessions.length()").value(3))
                .andExpect(jsonPath("$.sessions[0].id").value(pastId))
                .andExpect(jsonPath("$.sessions[0].status").value("MISSED"));

        mockMvc.perform(get("/api/v1/me/planned-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Haut du corps A"))
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].sets.length()").isNumber());

        // Les séances de Lucas n'existent pas pour Emma, même avec le bon identifiant.
        mockMvc.perform(get("/api/v1/me/planned-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, emmaToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/me/planned-sessions/today").header(HttpHeaders.AUTHORIZATION, emmaToken))
                .andExpect(jsonPath("$.sessions.length()").value(0));

        // US06 : sans coach, plus aucune lecture.
        jdbc.update("update coach_athlete set ended_at = now(6) where athlete_id = ?", bytes(lucas.getId().toString()));
        mockMvc.perform(get("/api/v1/me/planned-sessions/today").header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATHLETE_WITHOUT_COACH"));
        mockMvc.perform(get("/api/v1/me/planned-sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- outils

    private void follow(User coach, User athlete) {
        linkRepository.saveAndFlush(CoachAthlete.builder().coach(coach).athlete(athlete).joinedAt(Instant.now()).build());
    }

    private static String athleteSessions(User athlete) {
        return "/api/v1/athletes/" + athlete.getId() + "/planned-sessions";
    }

    private String planBody(LocalDate date) {
        return "{\"workoutId\":\"" + workout + "\",\"date\":\"" + date + "\"}";
    }

    private String planForLucas(LocalDate date) throws Exception {
        return mockMvc.perform(json(post(athleteSessions(lucas)), coachToken, planBody(date)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getContentAsString();
    }

    private void planForGroup(String group, LocalDate date, int expected) throws Exception {
        mockMvc.perform(json(post("/api/v1/groups/" + group + "/planned-sessions"), coachToken, planBody(date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(expected));
    }

    private String detail(String id) throws Exception {
        return mockMvc.perform(get(SESSIONS + id).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String athleteCalendar(String token, User athlete, LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get(athleteSessions(athlete)).header(HttpHeaders.AUTHORIZATION, token)
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String createGroup(String name, User... members) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/groups"), coachToken, "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = id(body);
        if (members.length > 0) {
            List<String> ids = new ArrayList<>();
            for (User member : members) {
                ids.add("\"" + member.getId() + "\"");
            }
            mockMvc.perform(json(post("/api/v1/groups/" + id + "/athletes"), coachToken,
                            "{\"athleteIds\":[" + String.join(",", ids) + "]}"))
                    .andExpect(status().is2xxSuccessful());
        }
        return id;
    }

    /** Développé couché (8 puis 6 répétitions, note), puis squat (1 série). */
    private static String workoutBody(String name, String... exercises) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < exercises.length; i++) {
            items.add(i == 0
                    ? "{\"exerciseId\":\"" + exercises[i] + "\",\"note\":\"Tempo 3-1-1\",\"sets\":["
                    + "{\"reps\":8,\"loadKg\":60,\"restSeconds\":90},{\"reps\":6,\"loadKg\":null,\"restSeconds\":null}]}"
                    : "{\"exerciseId\":\"" + exercises[i] + "\",\"note\":null,\"sets\":["
                    + "{\"reps\":5,\"loadKg\":80,\"restSeconds\":150}]}");
        }
        return "{\"name\":\"" + name + "\",\"description\":null,\"exercises\":[" + String.join(",", items) + "]}";
    }

    private String createWorkout(String token, String name, String... exercises) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/workouts"), token, workoutBody(name, exercises)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(body);
    }

    private String createExercise(String token, String name, String muscleGroup) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/exercises"), token,
                        "{\"name\":\"" + name + "\",\"muscleGroup\":\"" + muscleGroup + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(body);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/auth/login"), null,
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private static String id(String body) {
        return JsonPath.read(body, "$.id");
    }

    private int count(String tableAndWhere) {
        Integer count = jdbc.queryForObject("select count(*) from " + tableAndWhere, Integer.class);
        return count == null ? 0 : count;
    }

    private static byte[] bytes(String uuid) {
        UUID id = UUID.fromString(uuid);
        return ByteBuffer.allocate(16)
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
