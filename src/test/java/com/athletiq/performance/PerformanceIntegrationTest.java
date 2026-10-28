package com.athletiq.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.TestcontainersConfiguration;
import com.athletiq.athlete.CoachAthlete;
import com.athletiq.athlete.CoachAthleteRepository;
import com.athletiq.planning.PlanningClock;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Suivi et KPI sur une vraie base MySQL 8.4 (Testcontainers) : migration V8 validée par Hibernate et ses
 * contraintes, puis parcours HTTP avec de vrais JWT. Les réalisations sont écrites en SQL (l'écriture par
 * l'athlète arrive avec #10). « Aujourd'hui » est celui de l'application ({@link PlanningClock}) : les dates
 * sont relatives, et une séance passée est planifiée aujourd'hui puis déplacée en base (l'API refuse le passé).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PerformanceIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@athletiq.test";
    private static final String PASSWORD = "mot-de-passe-123";
    private static final String SESSIONS = "/api/v1/planned-sessions/";
    private static final String VALIDATED_AT = "2026-03-02 18:00:00";
    private static final String CORRECTED_AT = "2026-03-03 07:30:00";

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
    private String bench;
    private String pullUp;
    private String strength;
    private String claireWorkout;

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
        bench = createExercise(coachToken, "Développé couché", "PECTORAUX");
        pullUp = createExercise(coachToken, "Tractions", "DOS");
        // Développé couché 8 × 60, 8 × 60, 6 × 65, puis tractions 8 et 8 au poids du corps.
        strength = createWorkout(coachToken, "Force A", List.of(
                exerciseJson(bench, "Tempo 3-1-1", "8:60", "8:60", "6:65"),
                exerciseJson(pullUp, null, "8:", "8:")));
        String lunge = createExercise(claireToken, "Fentes", "QUADRICEPS");
        claireWorkout = createWorkout(claireToken, "Séance de Claire", List.of(exerciseJson(lunge, null, "10:20")));
    }

    /** Séances planifiées d'abord (les réalisations suivent en cascade) : les clés RESTRICT bloqueraient le reste. */
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
    void appliesMigrationV8WithItsUniqueCheckAndForeignKeyConstraints() throws Exception {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version = '8'", Integer.class);
        assertThat(applied).isEqualTo(1);
        String session = plan(coachToken, lucas, strength, today.minusDays(1));
        UUID performance = validate(session, lucas, 8, 4, "RAS", done(8, "60.0"));

        // RM4 : une seule réalisation par séance planifiée.
        assertThatThrownBy(() -> insertPerformance(session, lucas, "DRAFT", null, null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_session_performance_planned_session");
        jdbc.update("delete from session_performance where id = ?", (Object) bytes(performance));

        // Statut, RPE, fatigue, et validated_at présent si et seulement si la réalisation est validée.
        Map<String, Runnable> refused = Map.of(
                "ck_session_performance_status", () -> insertPerformance(session, lucas, "DONE", null, null, null),
                "ck_session_performance_rpe", () -> insertPerformance(session, lucas, "DRAFT", 0, null, null),
                "ck_session_performance_rpe ", () -> insertPerformance(session, lucas, "DRAFT", 11, null, null),
                "ck_session_performance_fatigue", () -> insertPerformance(session, lucas, "DRAFT", null, 6, null),
                "ck_session_performance_validated",
                () -> insertPerformance(session, lucas, "VALIDATED", null, null, null),
                "ck_session_performance_validated ",
                () -> insertPerformance(session, lucas, "DRAFT", null, null, VALIDATED_AT));
        refused.forEach((constraint, insert) -> assertThatThrownBy(insert::run)
                .as(constraint)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(constraint.strip()));
        assertThat(count("session_performance")).isZero();

        // Séries : répétitions 1..100 ou vides, charge au pas de 0,5, une ligne par série prévue.
        UUID draft = insertPerformance(session, lucas, "DRAFT", null, null, null);
        byte[] firstSet = snapshotSetIds(session).getFirst();
        for (String values : List.of("0, 60.0", "101, 60.0", "8, 60.2", "8, -1.0", "8, 1000.0")) {
            assertThatThrownBy(() -> jdbc.update("insert into performed_set (id, performance_id, snapshot_set_id, "
                    + "reps, load_kg, created_at, updated_at) values (?, ?, ?, " + values + ", now(6), now(6))",
                    bytes(UUID.randomUUID()), bytes(draft), firstSet))
                    .as(values)
                    .isInstanceOf(DataAccessException.class);
        }
        insertSet(draft, firstSet, null, null);
        assertThatThrownBy(() -> insertSet(draft, firstSet, 8, "60.0"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_performed_set_snapshot_set");
        // Une série d'instantané réalisée reste protégée.
        assertThatThrownBy(() -> jdbc.update("delete from session_snapshot_set where id = ?", (Object) firstSet))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_performed_set_snapshot_set");
    }

    // ---------------------------------------------------------------- US27, US29 historique

    @Test
    void theHistoryListsDoneAndMissedSessionsOfThisCoachMostRecentFirst() throws Exception {
        String old = plan(coachToken, lucas, strength, today.minusDays(40));
        validate(old, lucas, 6, 2, null, done(8, "55.0"));
        String d20 = plan(coachToken, lucas, strength, today.minusDays(20));
        validate(d20, lucas, 7, 3, null, done(8, "60.0"));
        String d13 = plan(coachToken, lucas, strength, today.minusDays(13));
        perform(d13, lucas, "VALIDATED", 8, 4, "Dernière série difficile", VALIDATED_AT, CORRECTED_AT,
                done(8, "62.5"));
        String d9 = plan(coachToken, lucas, strength, today.minusDays(9));
        String d6 = plan(coachToken, lucas, strength, today.minusDays(6));
        validate(d6, lucas, null, null, null, done(8, "60.0"));
        // Brouillon : invisible du coach, la séance passée reste « Non faite ».
        String d3 = plan(coachToken, lucas, strength, today.minusDays(3));
        perform(d3, lucas, "DRAFT", 9, 5, "Brouillon", null, null, done(8, "70.0"));
        plan(coachToken, lucas, strength, today);
        // Validée avant sa date (Q17) : réalisée, hors de la période par défaut.
        String ahead = plan(coachToken, lucas, strength, today.plusDays(2));
        validate(ahead, lucas, 5, 1, null, done(8, "60.0"));
        // La séance d'un autre coach n'apparaît jamais (RM1).
        String fromClaire = plan(claireToken, lucas, claireWorkout, today.minusDays(5));
        validate(fromClaire, lucas, 6, 3, "Avec Claire", done(10, "20.0"));

        String page = history(coachToken, lucas, Map.of());
        assertThat(JsonPath.<List<String>>read(page, "$.content[*].plannedSessionId"))
                .containsExactly(d3, d6, d9, d13, d20);
        assertThat(JsonPath.<List<String>>read(page, "$.content[*].status"))
                .containsExactly("MISSED", "DONE", "MISSED", "DONE", "DONE");
        assertThat(JsonPath.<Integer>read(page, "$.page.totalElements")).isEqualTo(5);
        assertThat(JsonPath.<Object>read(page, "$.content[0].rpe")).isNull();
        assertThat(JsonPath.<Boolean>read(page, "$.content[0].hasComment")).isFalse();
        assertThat(JsonPath.<Object>read(page, "$.content[1].rpe")).isNull();
        assertThat(JsonPath.<String>read(page, "$.content[3].name")).isEqualTo("Force A");
        assertThat(JsonPath.<Integer>read(page, "$.content[3].rpe")).isEqualTo(8);
        assertThat(JsonPath.<Integer>read(page, "$.content[3].fatigue")).isEqualTo(4);
        assertThat(JsonPath.<Boolean>read(page, "$.content[3].hasComment")).isTrue();
        assertThat(JsonPath.<String>read(page, "$.content[3].validatedAt")).isEqualTo("2026-03-02T18:00:00Z");
        assertThat(JsonPath.<String>read(page, "$.content[3].correctedAt")).isEqualTo("2026-03-03T07:30:00Z");

        assertThat(ids(history(coachToken, lucas, Map.of("status", "DONE")))).containsExactly(d6, d13, d20);
        assertThat(ids(history(coachToken, lucas, Map.of("status", "MISSED")))).containsExactly(d3, d9);
        assertThat(ids(history(coachToken, lucas, Map.of("withComment", "true")))).containsExactly(d13);
        assertThat(ids(history(coachToken, lucas, Map.of("from", today.minusDays(10).toString(),
                "to", today.minusDays(5).toString())))).containsExactly(d6, d9);
        assertThat(ids(history(coachToken, lucas, Map.of("to", today.plusDays(6).toString()))))
                .containsExactly(ahead, d3, d6, d9, d13, d20);
        assertThat(ids(history(coachToken, lucas, Map.of("from", today.minusDays(60).toString()))))
                .containsExactly(d3, d6, d9, d13, d20, old);
        String firstPage = history(coachToken, lucas, Map.of("size", "2"));
        assertThat(ids(firstPage)).containsExactly(d3, d6);
        assertThat(JsonPath.<Integer>read(firstPage, "$.page.totalPages")).isEqualTo(3);
        assertThat(ids(history(coachToken, lucas, Map.of("size", "2", "page", "2")))).containsExactly(d20);

        // Claire ne voit que sa propre séance avec Lucas.
        assertThat(ids(history(claireToken, lucas, Map.of()))).containsExactly(fromClaire);
    }

    @Test
    void theOverviewSaysWhetherTheAthleteHasAHistoryWithThisCoach() throws Exception {
        mockMvc.perform(get(tracking(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lucas"))
                .andExpect(jsonPath("$.lastName").value("Girard"))
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.hasPastSessions").value(false));

        // Une séance de Claire ne compte pas, une séance d'aujourd'hui non plus.
        validate(plan(claireToken, lucas, claireWorkout, today.minusDays(2)), lucas, 6, 3, null, done(10, "20.0"));
        String todaySession = plan(coachToken, lucas, strength, today);
        mockMvc.perform(get(tracking(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.hasPastSessions").value(false));

        // Réalisée aujourd'hui : elle entre dans l'historique.
        validate(todaySession, lucas, 7, 3, null, done(8, "60.0"));
        mockMvc.perform(get(tracking(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.hasPastSessions").value(true));
        // Passée sans réalisation : aussi.
        plan(coachToken, emma, strength, today.minusDays(1));
        mockMvc.perform(get(tracking(emma)).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.hasPastSessions").value(true));
    }

    @Test
    void invalidHistoryParametersAnswer400InvalidParameter() throws Exception {
        List<Map<String, String>> refused = List.of(
                Map.of("from", today.toString(), "to", today.minusDays(1).toString()),
                Map.of("from", today.minusDays(365).toString(), "to", today.toString()),
                Map.of("from", "2026-02-30"),
                Map.of("status", "PLANIFIEE"),
                Map.of("status", "UPCOMING"),
                Map.of("size", "101"));
        for (Map<String, String> parameters : refused) {
            MockHttpServletRequestBuilder request = get(historyUri(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken);
            parameters.forEach(request::param);
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        history(coachToken, lucas, Map.of("from", today.minusDays(364).toString(), "to", today.toString()));
    }

    // ---------------------------------------------------------------- US28, US29 prévu / réalisé

    @Test
    void theReviewComparesTheSnapshotWithThePerformedSetsEvenAfterTheWorkoutChanged() throws Exception {
        String session = plan(coachToken, lucas, strength, today.minusDays(2));
        // Développé couché : +2,5 kg, puis -2 répétitions, puis une série sans ligne ; tractions : 10, puis non faite.
        perform(session, lucas, "VALIDATED", 8, 4, "Dernière série difficile", VALIDATED_AT, CORRECTED_AT,
                done(8, "62.5"), done(6, "60.0"), noLine(), done(10, null), skipped());
        mockMvc.perform(json(put("/api/v1/exercises/" + bench), coachToken,
                        "{\"name\":\"Développé incliné\",\"muscleGroup\":\"PECTORAUX\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/workouts/" + strength).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());

        String review = review(coachToken, session);
        assertThat(JsonPath.<String>read(review, "$.status")).isEqualTo("DONE");
        assertThat(JsonPath.<String>read(review, "$.name")).isEqualTo("Force A");
        assertThat(JsonPath.<String>read(review, "$.athleteLastName")).isEqualTo("Girard");
        assertThat(JsonPath.<String>read(review, "$.today")).isEqualTo(today.toString());
        assertThat(JsonPath.<Integer>read(review, "$.feedback.rpe")).isEqualTo(8);
        assertThat(JsonPath.<String>read(review, "$.feedback.comment")).isEqualTo("Dernière série difficile");
        assertThat(JsonPath.<String>read(review, "$.feedback.correctedAt")).isEqualTo("2026-03-03T07:30:00Z");
        assertThat(JsonPath.<List<String>>read(review, "$.exercises[*].exerciseName"))
                .containsExactly("Développé couché", "Tractions");
        assertThat(JsonPath.<String>read(review, "$.exercises[0].exerciseId")).isEqualTo(bench);
        assertThat(JsonPath.<String>read(review, "$.exercises[0].note")).isEqualTo("Tempo 3-1-1");
        assertThat(JsonPath.<List<Integer>>read(review, "$.exercises[0].sets[*].plannedReps")).containsExactly(8, 8, 6);
        assertThat(JsonPath.<List<Boolean>>read(review, "$.exercises[0].sets[*].done"))
                .containsExactly(true, true, false);
        assertThat(JsonPath.<List<Integer>>read(review, "$.exercises[0].sets[*].repsDelta"))
                .containsExactly(0, -2, null);
        assertThat(JsonPath.<List<Double>>read(review, "$.exercises[0].sets[*].loadDeltaKg"))
                .containsExactly(2.5, 0.0, null);
        assertThat(JsonPath.<Double>read(review, "$.exercises[0].plannedVolumeKg")).isEqualTo(1350.0);
        assertThat(JsonPath.<Double>read(review, "$.exercises[0].performedVolumeKg")).isEqualTo(860.0);
        assertThat(JsonPath.<Integer>read(review, "$.exercises[0].volumeDeltaPercent")).isEqualTo(-36);
        assertThat(JsonPath.<Boolean>read(review, "$.exercises[1].bodyweight")).isTrue();
        assertThat(JsonPath.<Object>read(review, "$.exercises[1].plannedVolumeKg")).isNull();
        assertThat(JsonPath.<Object>read(review, "$.exercises[1].performedVolumeKg")).isNull();
        assertThat(JsonPath.<List<Integer>>read(review, "$.exercises[1].sets[*].repsDelta")).containsExactly(2, null);
        assertThat(JsonPath.<List<Object>>read(review, "$.exercises[1].sets[*].loadDeltaKg")).containsExactly(null, null);
        assertThat(JsonPath.<List<Boolean>>read(review, "$.exercises[1].sets[*].done")).containsExactly(true, false);
    }

    @Test
    void aMissedADraftAndAnUpcomingSessionShowThePlannedOnlyAndAnAheadValidationIsDone() throws Exception {
        String missed = plan(coachToken, lucas, strength, today.minusDays(1));
        String draft = plan(coachToken, lucas, strength, today.minusDays(2));
        perform(draft, lucas, "DRAFT", 9, 5, "Brouillon", null, null, done(8, "70.0"), done(8, "70.0"));
        String upcoming = plan(coachToken, lucas, strength, today.plusDays(1));
        String ahead = plan(coachToken, lucas, strength, today.plusDays(3));
        // Fatigue non renseignée : null, jamais 0.
        validate(ahead, lucas, 6, null, null, done(8, "60.0"));

        for (String id : List.of(missed, draft, upcoming)) {
            String review = review(coachToken, id);
            assertThat(JsonPath.<String>read(review, "$.status")).isEqualTo(id.equals(upcoming) ? "UPCOMING" : "MISSED");
            assertThat(JsonPath.<Object>read(review, "$.feedback")).isNull();
            assertThat(JsonPath.<Double>read(review, "$.exercises[0].plannedVolumeKg")).isEqualTo(1350.0);
            assertThat(JsonPath.<Object>read(review, "$.exercises[0].performedVolumeKg")).isNull();
            assertThat(JsonPath.<List<Boolean>>read(review, "$.exercises[*].sets[*].done")).containsOnly(false);
            assertThat(JsonPath.<List<Object>>read(review, "$.exercises[*].sets[*].performedReps")).containsOnlyNulls();
        }
        String done = review(coachToken, ahead);
        assertThat(JsonPath.<String>read(done, "$.status")).isEqualTo("DONE");
        assertThat(JsonPath.<Integer>read(done, "$.feedback.rpe")).isEqualTo(6);
        assertThat(JsonPath.<Object>read(done, "$.feedback.fatigue")).isNull();
    }

    // ---------------------------------------------------------------- propriété (RM1)

    @Test
    void anotherCoachOrAClosedLinkAnswers404AndAReinvitationBringsTheHistoryBack() throws Exception {
        String nicolasSession = plan(coachToken, lucas, strength, today.minusDays(2));
        validate(nicolasSession, lucas, 7, 3, null, done(8, "60.0"));
        String claireSession = plan(claireToken, lucas, claireWorkout, today.minusDays(2));
        String hugoSession = plan(coachToken, hugo, strength, today.minusDays(3));
        validate(hugoSession, hugo, 7, 3, null, done(8, "60.0"));

        expect404(claireToken, SESSIONS + nicolasSession + "/review", "PLANNED_SESSION_NOT_FOUND");
        expect404(coachToken, SESSIONS + claireSession + "/review", "PLANNED_SESSION_NOT_FOUND");
        expect404(claireToken, historyUri(emma), "ATHLETE_NOT_FOUND");
        expect404(claireToken, tracking(emma), "ATHLETE_NOT_FOUND");
        expect404(coachToken, SESSIONS + UUID.randomUUID() + "/review", "PLANNED_SESSION_NOT_FOUND");

        // Lien clos : plus rien n'est visible, mais la séance réalisée reste en base.
        mockMvc.perform(delete("/api/v1/athletes/" + hugo.getId()).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        expect404(coachToken, historyUri(hugo), "ATHLETE_NOT_FOUND");
        expect404(coachToken, tracking(hugo), "ATHLETE_NOT_FOUND");
        expect404(coachToken, SESSIONS + hugoSession + "/review", "PLANNED_SESSION_NOT_FOUND");
        assertThat(count("session_performance")).isEqualTo(2);

        // Q6 : après une ré-invitation, l'historique est retrouvé.
        follow(coach, hugo);
        assertThat(ids(history(coachToken, hugo, Map.of()))).containsExactly(hugoSession);
        assertThat(JsonPath.<String>read(review(coachToken, hugoSession), "$.status")).isEqualTo("DONE");
    }

    // ---------------------------------------------------------------- reprise #6 : verrouillage et P7

    @Test
    void aDoneSessionIsLockedEvenAheadAndUnfollowingKeepsItWhileADraftLeavesWithItsSession() throws Exception {
        String past = plan(coachToken, lucas, strength, today.minusDays(1));
        String ahead = plan(coachToken, lucas, strength, today.plusDays(2));
        validate(ahead, lucas, 6, 2, null, done(8, "60.0"));
        String drafted = plan(coachToken, lucas, strength, today.plusDays(3));
        perform(drafted, lucas, "DRAFT", null, null, null, null, null, done(8, "60.0"), done(8, "60.0"));
        String upcoming = plan(coachToken, lucas, strength, today.plusDays(4));
        // Un brouillon ne verrouille pas : la séance s'annule, et le brouillon part avec elle.
        String cancelled = plan(coachToken, lucas, strength, today.plusDays(5));
        perform(cancelled, lucas, "DRAFT", 7, null, null, null, null, done(8, "60.0"));
        mockMvc.perform(delete(SESSIONS + cancelled).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        assertThat(count("session_performance")).isEqualTo(2);

        String calendar = mockMvc.perform(get(athleteSessions(lucas)).header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", today.minusDays(1).toString()).param("to", today.plusDays(4).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(calendar, "$.sessions[*].id")).containsExactly(past, ahead, drafted, upcoming);
        assertThat(JsonPath.<List<String>>read(calendar, "$.sessions[*].status"))
                .containsExactly("MISSED", "DONE", "UPCOMING", "UPCOMING");
        assertThat(JsonPath.<List<Boolean>>read(calendar, "$.sessions[*].locked")).containsExactly(true, true, false, false);
        mockMvc.perform(get(SESSIONS + ahead).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.locked").value(true));

        // Réalisée, même future : ni déplacée, ni annulée.
        mockMvc.perform(json(patch(SESSIONS + ahead), coachToken, "{\"date\":\"" + today.plusDays(5) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_LOCKED"));
        mockMvc.perform(delete(SESSIONS + ahead).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANNED_SESSION_LOCKED"));
        // Le compteur de retrait suit P7 : la séance réalisée ne serait pas supprimée.
        mockMvc.perform(get(athleteSessions(lucas) + "/upcoming-count").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(delete("/api/v1/athletes/" + lucas.getId()).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());

        assertThat(plannedSessionIds()).containsExactlyInAnyOrder(past, ahead);
        // Le brouillon et ses séries sont partis en cascade ; la réalisation validée et ses séries restent.
        assertThat(count("session_performance")).isEqualTo(1);
        assertThat(count("session_performance where status = 'VALIDATED'")).isEqualTo(1);
        assertThat(count("performed_set")).isEqualTo(1);
        assertThat(count("session_snapshot")).isEqualTo(2);
    }

    @Test
    void theGroupCalendarGivesEachMemberSessionItsOwnStatus() throws Exception {
        String group = createGroup("Sprint", lucas, emma);
        LocalDate day = today.plusDays(1);
        mockMvc.perform(json(post("/api/v1/groups/" + group + "/planned-sessions"), coachToken,
                        "{\"workoutId\":\"" + strength + "\",\"date\":\"" + day + "\"}"))
                .andExpect(status().isCreated());
        String lucasSession = JsonPath.<List<String>>read(calendar(lucas, day), "$.sessions[*].id").getFirst();
        validate(lucasSession, lucas, 7, 3, null, done(8, "60.0"));

        String calendar = mockMvc.perform(get("/api/v1/groups/" + group + "/planned-sessions")
                        .header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(calendar, "$.entries[0].sessions[*].lastName"))
                .containsExactly("Girard", "Lefèvre");
        assertThat(JsonPath.<List<String>>read(calendar, "$.entries[0].sessions[*].status"))
                .containsExactly("DONE", "UPCOMING");
        assertThat(JsonPath.<Boolean>read(calendar, "$.entries[0].locked")).isFalse();
    }

    // ---------------------------------------------------------------- outils : réalisations écrites en SQL

    /** Série réalisée ; {@code reps} vide = série non faite. */
    record Done(Integer reps, String loadKg) {
    }

    static Done done(int reps, String loadKg) {
        return new Done(reps, loadKg);
    }

    /** Une ligne dont les répétitions sont vides : série non faite. */
    static Done skipped() {
        return new Done(null, null);
    }

    /** Aucune ligne pour cette série prévue : elle est aussi non faite. */
    static Done noLine() {
        return null;
    }

    @Test
    void anAthleteEntersDraftsValidatesAndCorrectsASessionAndTheCoachOnlyReadsTheValidatedOne() throws Exception {
        String sessionId = plan(coachToken, lucas, strength, today);
        String lucasToken = login("lucas.girard@athlete.fr");
        String emmaToken = login("emma.lefevre@athlete.fr");
        String own = "/api/v1/me/planned-sessions/" + sessionId;
        String detail = mockMvc.perform(get(own).header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String firstSet = JsonPath.read(detail, "$.exercises[0].sets[0].id");
        String secondSet = JsonPath.read(detail, "$.exercises[0].sets[1].id");

        mockMvc.perform(get(own + "/performance").header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").doesNotExist());

        // Brouillon : invisible du coach, la séance reste à venir.
        mockMvc.perform(json(put(own + "/performance"), lucasToken, "{\"sets\":["
                        + "{\"snapshotSetId\":\"" + firstSet + "\",\"reps\":8,\"loadKg\":62.5}],\"rpe\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.validatedAt").doesNotExist());
        assertThat(JsonPath.<String>read(review(coachToken, sessionId), "$.status")).isEqualTo("UPCOMING");
        mockMvc.perform(get(own).header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(jsonPath("$.status").value("UPCOMING"));

        // Une séance sans aucune série faite ne se valide pas, et rien n'est enregistré par la tentative.
        mockMvc.perform(json(post(own + "/performance/validate"), lucasToken,
                        "{\"sets\":[{\"snapshotSetId\":\"" + firstSet + "\",\"reps\":null}],\"rpe\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_EMPTY"));
        mockMvc.perform(get(own + "/performance").header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(jsonPath("$.sets[0].reps").value(8));

        // Validation : la séance est réalisée, verrouillée, et le coach lit le réalisé et le ressenti.
        mockMvc.perform(json(post(own + "/performance/validate"), lucasToken, "{\"sets\":["
                        + "{\"snapshotSetId\":\"" + firstSet + "\",\"reps\":8,\"loadKg\":62.5},"
                        + "{\"snapshotSetId\":\"" + secondSet + "\",\"reps\":7,\"loadKg\":60}],"
                        + "\"rpe\":8,\"fatigue\":3,\"comment\":\"  Dernière série lourde  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.correctedAt").doesNotExist());
        mockMvc.perform(get(own).header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(jsonPath("$.status").value("DONE"));
        String review = review(coachToken, sessionId);
        assertThat(JsonPath.<String>read(review, "$.status")).isEqualTo("DONE");
        assertThat(JsonPath.<Integer>read(review, "$.feedback.rpe")).isEqualTo(8);
        assertThat(JsonPath.<String>read(review, "$.feedback.comment")).isEqualTo("Dernière série lourde");
        assertThat(JsonPath.<Integer>read(review, "$.exercises[0].sets[1].performedReps")).isEqualTo(7);
        assertThat(JsonPath.<Boolean>read(calendar(lucas, today), "$.sessions[0].locked")).isTrue();

        // Correction (US14) : reste validée, correctedAt daté, le coach lit les valeurs corrigées.
        mockMvc.perform(json(put(own + "/performance"), lucasToken, "{\"sets\":["
                        + "{\"snapshotSetId\":\"" + firstSet + "\",\"reps\":9,\"loadKg\":62.5}],\"rpe\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.correctedAt").exists())
                .andExpect(jsonPath("$.sets.length()").value(1));
        review = review(coachToken, sessionId);
        assertThat(JsonPath.<Integer>read(review, "$.exercises[0].sets[0].performedReps")).isEqualTo(9);
        assertThat(JsonPath.<Object>read(review, "$.exercises[0].sets[1].performedReps")).isNull();
        assertThat(JsonPath.<String>read(review, "$.feedback.correctedAt")).isNotNull();
        assertThat(count("session_performance")).isEqualTo(1);
        assertThat(count("performed_set")).isEqualTo(1);

        // Isolement et validation des séries.
        mockMvc.perform(get(own + "/performance").header(HttpHeaders.AUTHORIZATION, emmaToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLANNED_SESSION_NOT_FOUND"));
        mockMvc.perform(json(put(own + "/performance"), emmaToken, "{\"sets\":[]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(put(own + "/performance"), lucasToken, "{\"sets\":["
                        + "{\"snapshotSetId\":\"" + UUID.randomUUID() + "\",\"reps\":8}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERFORMED_SET_UNKNOWN"));
        mockMvc.perform(json(put(own + "/performance"), lucasToken, "{\"sets\":["
                        + "{\"snapshotSetId\":\"" + firstSet + "\",\"reps\":8},"
                        + "{\"snapshotSetId\":\"" + firstSet + "\",\"reps\":7}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERFORMED_SET_DUPLICATE"));

        // US06 : sans coach actif, plus aucune saisie.
        jdbc.update("update coach_athlete set ended_at = now(6) where athlete_id = ?", bytes(lucas.getId()));
        mockMvc.perform(get(own + "/performance").header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATHLETE_WITHOUT_COACH"));
    }

    @Test
    void anAthleteReadsHerOwnHistoryRecordsProgressionAndKpisAcrossAllHerCoaches() throws Exception {
        String d20 = plan(coachToken, lucas, strength, today.minusDays(20));
        validate(d20, lucas, 8, 4, "Lourd", done(8, "60.0"), done(8, "60.0"), done(6, "65.0"), done(8, null), done(8, null));
        String d10 = plan(coachToken, lucas, strength, today.minusDays(10));
        validate(d10, lucas, 6, 3, null, done(8, "62.5"), skipped(), noLine(), done(10, null), skipped());
        String d5 = plan(coachToken, lucas, strength, today.minusDays(5));
        String d3 = plan(coachToken, lucas, strength, today.minusDays(3));
        perform(d3, lucas, "DRAFT", 9, null, null, null, null, done(8, "100.0"));
        String claireSession = plan(claireToken, lucas, claireWorkout, today.minusDays(2));
        validate(claireSession, lucas, 7, 2, null, done(12, "30.0"));
        validate(plan(coachToken, emma, strength, today.minusDays(4)), emma, 5, 1, null, done(8, "200.0"));
        String lucasToken = login("lucas.girard@athlete.fr");
        String emmaToken = login("emma.lefevre@athlete.fr");

        // US15 : toutes ses séances, tous coachs confondus, le brouillon compte comme non fait.
        String history = read(lucasToken, "/api/v1/me/session-history");
        assertThat(JsonPath.<List<String>>read(history, "$.content[*].plannedSessionId"))
                .containsExactly(claireSession, d3, d5, d10, d20);
        assertThat(JsonPath.<List<String>>read(history, "$.content[*].status"))
                .containsExactly("DONE", "MISSED", "MISSED", "DONE", "DONE");
        assertThat(JsonPath.<List<String>>read(read(lucasToken, "/api/v1/me/session-history?status=MISSED"),
                "$.content[*].plannedSessionId")).containsExactly(d3, d5);
        assertThat(JsonPath.<List<String>>read(read(lucasToken, "/api/v1/me/session-history?withComment=true"),
                "$.content[*].plannedSessionId")).containsExactly(d20);
        assertThat(JsonPath.<List<String>>read(read(emmaToken, "/api/v1/me/session-history"),
                "$.content[*].status")).containsExactly("DONE");

        // US16 : meilleure charge par exercice, séries d'Emma et brouillon exclus.
        String records = read(lucasToken, "/api/v1/me/personal-records");
        assertThat(JsonPath.<List<String>>read(records, "$.records[*].exerciseName"))
                .containsExactly("Tractions", "Développé couché", "Fentes");
        assertThat(JsonPath.<List<Object>>read(records, "$.records[*].loadKg")).containsExactly(null, 65.0, 30.0);
        assertThat(JsonPath.<List<Integer>>read(records, "$.records[*].reps")).containsExactly(10, 6, 12);
        // L'ancien record : tractions battues (8 puis 10), les deux autres sont des premiers records.
        assertThat(JsonPath.<List<Integer>>read(records, "$.records[*].previousReps")).containsExactly(8, null, null);
        assertThat(JsonPath.<List<String>>read(records, "$.records[*].date"))
                .containsExactly(today.minusDays(10).toString(), today.minusDays(20).toString(),
                        today.minusDays(2).toString());

        // US17 : exercices réalisés puis progression, un point par séance réalisée.
        String exercises = read(lucasToken, "/api/v1/me/performed-exercises");
        assertThat(JsonPath.<List<String>>read(exercises, "$[*].exerciseName"))
                .containsExactly("Tractions", "Développé couché", "Fentes");
        assertThat(JsonPath.<List<Integer>>read(exercises, "$[*].sessionCount")).containsExactly(2, 2, 1);
        String progression = read(lucasToken, "/api/v1/me/exercises/" + bench + "/progression");
        assertThat(JsonPath.<String>read(progression, "$.exerciseName")).isEqualTo("Développé couché");
        assertThat(JsonPath.<Integer>read(progression, "$.weeks")).isEqualTo(12);
        assertThat(JsonPath.<List<String>>read(progression, "$.points[*].plannedSessionId")).containsExactly(d20, d10);
        assertThat(JsonPath.<List<Double>>read(progression, "$.points[*].maxLoadKg")).containsExactly(65.0, 62.5);
        assertThat(JsonPath.<List<Double>>read(progression, "$.points[*].volumeKg")).containsExactly(1350.0, 500.0);
        mockMvc.perform(get("/api/v1/me/exercises/" + UUID.randomUUID() + "/progression")
                        .header(HttpHeaders.AUTHORIZATION, lucasToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXERCISE_NOT_FOUND"));
        // Emma n'a jamais réalisé l'exercice de Claire : introuvable pour elle.
        mockMvc.perform(get("/api/v1/me/exercises/" + JsonPath.read(exercises, "$[2].exerciseId") + "/progression")
                        .header(HttpHeaders.AUTHORIZATION, emmaToken))
                .andExpect(status().isNotFound());

        // US18 : 3 réalisées, 2 non faites (dont le brouillon), RPE moyen (8 + 6 + 7) / 3.
        String kpis = read(lucasToken, "/api/v1/me/kpis");
        assertThat(JsonPath.<Integer>read(kpis, "$.days")).isEqualTo(30);
        assertThat(JsonPath.<String>read(kpis, "$.from")).isEqualTo(today.minusDays(30).toString());
        assertThat(JsonPath.<String>read(kpis, "$.to")).isEqualTo(today.minusDays(1).toString());
        assertThat(JsonPath.<Integer>read(kpis, "$.doneCount")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(kpis, "$.missedCount")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(kpis, "$.completionRate")).isEqualTo(60);
        assertThat(JsonPath.<Double>read(kpis, "$.averageRpe")).isEqualTo(7.0);
        assertThat(JsonPath.<Integer>read(kpis, "$.recentMissedCount")).isEqualTo(2);
        assertThat(JsonPath.<Object>read(read(emmaToken, "/api/v1/me/kpis?days=90"), "$.completionRate")).isEqualTo(100);

        // Paramètres refusés, rôles et accès sans coach.
        for (String uri : List.of("/api/v1/me/kpis?days=5", "/api/v1/me/exercises/" + bench + "/progression?weeks=5",
                "/api/v1/me/session-history?status=UPCOMING", "/api/v1/me/session-history?size=101")) {
            mockMvc.perform(get(uri).header(HttpHeaders.AUTHORIZATION, lucasToken)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/me/kpis").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/kpis")).andExpect(status().isUnauthorized());
        jdbc.update("update coach_athlete set ended_at = now(6) where athlete_id = ?", bytes(lucas.getId()));
        for (String uri : List.of("/api/v1/me/session-history", "/api/v1/me/personal-records",
                "/api/v1/me/performed-exercises", "/api/v1/me/kpis")) {
            mockMvc.perform(get(uri).header(HttpHeaders.AUTHORIZATION, lucasToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ATHLETE_WITHOUT_COACH"));
        }
    }

    private UUID validate(String plannedSessionId, User athlete, Integer rpe, Integer fatigue, String comment,
                          Done... sets) {
        return perform(plannedSessionId, athlete, "VALIDATED", rpe, fatigue, comment, VALIDATED_AT, null, sets);
    }

    /** Les séries sont données dans l'ordre de l'instantané (position d'exercice, puis de série). */
    private UUID perform(String plannedSessionId, User athlete, String status, Integer rpe, Integer fatigue,
                         String comment, String validatedAt, String correctedAt, Done... sets) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into session_performance (id, planned_session_id, athlete_id, status, rpe, fatigue, comment, "
                        + "validated_at, corrected_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(6), now(6))",
                bytes(id), bytes(plannedSessionId), bytes(athlete.getId()), status, rpe, fatigue, comment, validatedAt,
                correctedAt);
        List<byte[]> setIds = snapshotSetIds(plannedSessionId);
        for (int i = 0; i < sets.length; i++) {
            if (sets[i] != null) {
                insertSet(id, setIds.get(i), sets[i].reps(), sets[i].loadKg());
            }
        }
        return id;
    }

    private UUID insertPerformance(String plannedSessionId, User athlete, String status, Integer rpe, Integer fatigue,
                                   String validatedAt) {
        return perform(plannedSessionId, athlete, status, rpe, fatigue, null, validatedAt, null);
    }

    private void insertSet(UUID performanceId, byte[] snapshotSetId, Integer reps, String loadKg) {
        jdbc.update("insert into performed_set (id, performance_id, snapshot_set_id, reps, load_kg, created_at, "
                        + "updated_at) values (?, ?, ?, ?, ?, now(6), now(6))",
                bytes(UUID.randomUUID()), bytes(performanceId), snapshotSetId, reps,
                loadKg == null ? null : new BigDecimal(loadKg));
    }

    private List<byte[]> snapshotSetIds(String plannedSessionId) {
        return jdbc.queryForList("""
                select st.id from session_snapshot_set st
                join session_snapshot_exercise e on e.id = st.snapshot_exercise_id
                join planned_session ps on ps.snapshot_id = e.snapshot_id
                where ps.id = ?
                order by e.position, st.position
                """, byte[].class, (Object) bytes(plannedSessionId));
    }

    // ---------------------------------------------------------------- outils : HTTP

    /** Planifie par l'API (aujourd'hui au plus tôt), puis déplace en base une séance passée. */
    private String plan(String token, User athlete, String workoutId, LocalDate date) throws Exception {
        LocalDate planned = date.isBefore(today) ? today : date;
        String body = mockMvc.perform(json(post(athleteSessions(athlete)), token,
                        "{\"workoutId\":\"" + workoutId + "\",\"date\":\"" + planned + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        if (!planned.equals(date)) {
            jdbc.update("update planned_session set scheduled_date = ? where id = ?", date, bytes(id));
        }
        return id;
    }

    private String history(String token, User athlete, Map<String, String> parameters) throws Exception {
        MockHttpServletRequestBuilder request = get(historyUri(athlete)).header(HttpHeaders.AUTHORIZATION, token);
        parameters.forEach(request::param);
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String review(String token, String plannedSessionId) throws Exception {
        return mockMvc.perform(get(SESSIONS + plannedSessionId + "/review").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String calendar(User athlete, LocalDate day) throws Exception {
        return mockMvc.perform(get(athleteSessions(athlete)).header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void expect404(String token, String uri, String code) throws Exception {
        mockMvc.perform(get(uri).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(code));
    }

    private static List<String> ids(String page) {
        return JsonPath.read(page, "$.content[*].plannedSessionId");
    }

    private static String historyUri(User athlete) {
        return "/api/v1/athletes/" + athlete.getId() + "/session-history";
    }

    private static String tracking(User athlete) {
        return "/api/v1/athletes/" + athlete.getId() + "/tracking";
    }

    private static String athleteSessions(User athlete) {
        return "/api/v1/athletes/" + athlete.getId() + "/planned-sessions";
    }

    private List<String> plannedSessionIds() {
        return jdbc.queryForList("select bin_to_uuid(id) from planned_session", String.class);
    }

    private void follow(User coach, User athlete) {
        linkRepository.saveAndFlush(CoachAthlete.builder().coach(coach).athlete(athlete).joinedAt(Instant.now()).build());
    }

    private String createGroup(String name, User... members) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/groups"), coachToken, "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        List<String> ids = new ArrayList<>();
        for (User member : members) {
            ids.add("\"" + member.getId() + "\"");
        }
        mockMvc.perform(json(post("/api/v1/groups/" + id + "/athletes"), coachToken,
                        "{\"athleteIds\":[" + String.join(",", ids) + "]}"))
                .andExpect(status().is2xxSuccessful());
        return id;
    }

    /** Séries au format {@code "reps:charge"}, charge vide = poids du corps. */
    private static String exerciseJson(String exerciseId, String note, String... sets) {
        List<String> items = new ArrayList<>();
        for (String set : sets) {
            String[] parts = set.split(":", -1);
            items.add("{\"reps\":" + parts[0] + ",\"loadKg\":" + (parts[1].isEmpty() ? "null" : parts[1])
                    + ",\"restSeconds\":90}");
        }
        return "{\"exerciseId\":\"" + exerciseId + "\",\"note\":" + (note == null ? "null" : "\"" + note + "\"")
                + ",\"sets\":[" + String.join(",", items) + "]}";
    }

    private String createWorkout(String token, String name, List<String> exercises) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/workouts"), token, "{\"name\":\"" + name
                        + "\",\"description\":null,\"exercises\":[" + String.join(",", exercises) + "]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
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
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private int count(String tableAndWhere) {
        Integer count = jdbc.queryForObject("select count(*) from " + tableAndWhere, Integer.class);
        return count == null ? 0 : count;
    }

    private static byte[] bytes(String uuid) {
        return bytes(UUID.fromString(uuid));
    }

    private static byte[] bytes(UUID id) {
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

    // ---------------------------------------------------------------- US30, US31 progression et records

    @Test
    void performedExercisesProgressionAndRecordsComeFromTheDoneSetsOfThisCoachOnly() throws Exception {
        String squat = createExercise(coachToken, "Squat", "QUADRICEPS");
        // Squat 5 × 100, 5 × 100 ; tractions 6 au poids du corps ; squat à nouveau 3 × 110 (exercice répété).
        String legs = createWorkout(coachToken, "Jambes", List.of(exerciseJson(squat, null, "5:100", "5:100"),
                exerciseJson(pullUp, null, "6:"), exerciseJson(squat, null, "3:110")));
        String d100 = plan(coachToken, lucas, legs, today.minusDays(100));
        UUID d100Performance = validate(d100, lucas, 9, 4, null,
                done(5, "120.0"), done(5, "120.0"), done(6, null), done(3, "120.0"));
        String d30 = plan(coachToken, lucas, legs, today.minusDays(30));
        validate(d30, lucas, 7, 3, null, done(5, "100.0"), done(5, "100.0"), done(6, null), done(3, "110.0"));
        String d20 = plan(coachToken, lucas, legs, today.minusDays(20));
        validate(d20, lucas, 8, 3, null, done(5, "110.0"), done(4, "105.0"), done(8, null), noLine());
        String d10 = plan(coachToken, lucas, legs, today.minusDays(10));
        validate(d10, lucas, 8, 4, null, done(5, "110.0"), skipped(), done(10, null), done(2, "110.0"));
        plan(coachToken, lucas, legs, today.minusDays(15));
        String drafted = plan(coachToken, lucas, legs, today.minusDays(5));
        perform(drafted, lucas, "DRAFT", null, null, null, null, null, done(5, "200.0"), done(5, "200.0"),
                done(30, null), done(5, "200.0"));
        validate(plan(claireToken, lucas, claireWorkout, today.minusDays(4)), lucas, 6, 3, null, done(12, "30.0"));
        // RM6 : renommé après coup, l'exercice prend le libellé de l'instantané le plus récent.
        mockMvc.perform(json(put("/api/v1/exercises/" + squat), coachToken,
                        "{\"name\":\"Squat barre\",\"muscleGroup\":\"QUADRICEPS\"}"))
                .andExpect(status().isOk());
        // Q17 : validée avant sa date, elle compte déjà (pas de borne haute).
        String ahead = plan(coachToken, lucas, legs, today.plusDays(1));
        validate(ahead, lucas, 6, 2, null, done(5, "100.0"), done(5, "100.0"), done(6, null), done(3, "100.0"));

        String exercises = read(coachToken, "/api/v1/athletes/" + lucas.getId() + "/performed-exercises");
        assertThat(JsonPath.<List<String>>read(exercises, "$[*].exerciseName")).containsExactly("Tractions", "Squat barre");
        assertThat(JsonPath.<List<String>>read(exercises, "$[*].exerciseId")).containsExactly(pullUp, squat);
        assertThat(JsonPath.<List<Integer>>read(exercises, "$[*].sessionCount")).containsExactly(5, 5);
        assertThat(JsonPath.<List<String>>read(exercises, "$[*].lastPerformedOn"))
                .containsOnly(today.plusDays(1).toString());

        String progression = read(coachToken, progressionUri(lucas, squat));
        assertThat(JsonPath.<String>read(progression, "$.exerciseName")).isEqualTo("Squat barre");
        assertThat(JsonPath.<Boolean>read(progression, "$.bodyweight")).isFalse();
        assertThat(JsonPath.<Integer>read(progression, "$.weeks")).isEqualTo(12);
        assertThat(JsonPath.<String>read(progression, "$.from")).isEqualTo(today.minusDays(84).toString());
        assertThat(JsonPath.<List<String>>read(progression, "$.points[*].plannedSessionId"))
                .containsExactly(d30, d20, d10, ahead);
        assertThat(JsonPath.<List<Double>>read(progression, "$.points[*].maxLoadKg"))
                .containsExactly(110.0, 110.0, 110.0, 100.0);
        assertThat(JsonPath.<List<Integer>>read(progression, "$.points[*].maxReps")).containsExactly(5, 5, 5, 5);
        // Séries réalisées seulement, les deux squats de la séance agrégés : 5 × 100 + 5 × 100 + 3 × 110 = 1330.
        assertThat(JsonPath.<List<Double>>read(progression, "$.points[*].volumeKg"))
                .containsExactly(1330.0, 970.0, 770.0, 1300.0);
        assertThat(JsonPath.<List<String>>read(read(coachToken, progressionUri(lucas, squat) + "?weeks=26"),
                "$.points[*].plannedSessionId")).containsExactly(d100, d30, d20, d10, ahead);

        String pullUps = read(coachToken, progressionUri(lucas, pullUp) + "?weeks=4");
        assertThat(JsonPath.<Boolean>read(pullUps, "$.bodyweight")).isTrue();
        assertThat(JsonPath.<List<Integer>>read(pullUps, "$.points[*].maxReps")).containsExactly(8, 10, 6);
        assertThat(JsonPath.<List<Object>>read(pullUps, "$.points[*].maxLoadKg")).containsOnlyNulls();
        assertThat(JsonPath.<List<Object>>read(pullUps, "$.points[*].volumeKg")).containsOnlyNulls();

        // Jamais réalisé : aucun point, libellé de la bibliothèque.
        String never = read(coachToken, progressionUri(lucas, bench));
        assertThat(JsonPath.<List<Object>>read(never, "$.points")).isEmpty();
        assertThat(JsonPath.<String>read(never, "$.exerciseName")).isEqualTo("Développé couché");
        assertThat(JsonPath.<String>read(never, "$.muscleGroup")).isEqualTo("PECTORAUX");

        String claireExercise = JsonPath.read(read(claireToken, "/api/v1/athletes/" + lucas.getId()
                + "/performed-exercises"), "$[0].exerciseId");
        expect404(coachToken, progressionUri(lucas, claireExercise), "EXERCISE_NOT_FOUND");
        mockMvc.perform(get(progressionUri(lucas, squat)).header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("weeks", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        // Records : 120 kg au squat il y a 100 jours ; tractions, 10 répétitions il y a 10 jours.
        String records = read(coachToken, recordsUri(lucas));
        assertThat(JsonPath.<List<String>>read(records, "$.records[*].exerciseName"))
                .containsExactly("Tractions", "Squat barre");
        assertThat(JsonPath.<Boolean>read(records, "$.records[0].bodyweight")).isTrue();
        assertThat(JsonPath.<Object>read(records, "$.records[0].loadKg")).isNull();
        assertThat(JsonPath.<Integer>read(records, "$.records[0].reps")).isEqualTo(10);
        assertThat(JsonPath.<String>read(records, "$.records[0].plannedSessionId")).isEqualTo(d10);
        assertThat(JsonPath.<Double>read(records, "$.records[1].loadKg")).isEqualTo(120.0);
        assertThat(JsonPath.<Integer>read(records, "$.records[1].reps")).isEqualTo(5);
        assertThat(JsonPath.<String>read(records, "$.records[1].date")).isEqualTo(today.minusDays(100).toString());

        // Correction qui baisse la série du record : recalculé. À 110 × 5, la date la plus ancienne l'emporte.
        jdbc.update("update performed_set set load_kg = 100.0 where performance_id = ? and load_kg = 120.0",
                (Object) bytes(d100Performance));
        String corrected = read(coachToken, recordsUri(lucas));
        assertThat(JsonPath.<Double>read(corrected, "$.records[1].loadKg")).isEqualTo(110.0);
        assertThat(JsonPath.<Integer>read(corrected, "$.records[1].reps")).isEqualTo(5);
        assertThat(JsonPath.<String>read(corrected, "$.records[1].plannedSessionId")).isEqualTo(d20);

        // Claire ne voit que ce que Lucas a fait avec elle.
        assertThat(JsonPath.<List<String>>read(read(claireToken, recordsUri(lucas)), "$.records[*].exerciseName"))
                .containsExactly("Fentes");

        // Lien clos : 404, et la séance validée à venir reste (P7).
        mockMvc.perform(delete("/api/v1/athletes/" + lucas.getId()).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        expect404(coachToken, recordsUri(lucas), "ATHLETE_NOT_FOUND");
        expect404(coachToken, "/api/v1/athletes/" + lucas.getId() + "/performed-exercises", "ATHLETE_NOT_FOUND");
        expect404(coachToken, progressionUri(lucas, squat), "ATHLETE_NOT_FOUND");
        assertThat(plannedSessionIds()).contains(ahead, drafted);
    }

    // ---------------------------------------------------------------- US32 tableau de bord

    @Test
    void theKpisCountOnlyThePastSessionsOfThisCoachForItsFollowedAthletes() throws Exception {
        User nathan = userService.create("nathan.chevalier@athlete.fr", PASSWORD, "Nathan", "Chevalier", Role.ATHLETE);
        follow(coach, nathan);
        // Lucas : 2 réalisées, 1 non faite et 1 brouillon (non faite) dans les 7 derniers jours.
        validate(plan(coachToken, lucas, strength, today.minusDays(20)), lucas, 8, 4, null, done(8, "60.0"));
        validate(plan(coachToken, lucas, strength, today.minusDays(10)), lucas, 6, 3, null, done(8, "60.0"));
        plan(coachToken, lucas, strength, today.minusDays(5));
        perform(plan(coachToken, lucas, strength, today.minusDays(3)), lucas, "DRAFT", 9, null, null, null, null,
                done(8, "60.0"));
        validate(plan(coachToken, lucas, strength, today.minusDays(40)), lucas, 5, 2, null, done(8, "60.0"));
        // Aujourd'hui et demain ne comptent pas, même réalisées.
        validate(plan(coachToken, lucas, strength, today), lucas, 10, 5, null, done(8, "60.0"));
        validate(plan(coachToken, lucas, strength, today.plusDays(1)), lucas, 10, 5, null, done(8, "60.0"));
        // Emma : 3 réalisées sur 3, dont une sans RPE ; une séance d'aujourd'hui pas encore faite.
        validate(plan(coachToken, emma, strength, today.minusDays(15)), emma, 7, 3, null, done(8, "60.0"));
        validate(plan(coachToken, emma, strength, today.minusDays(8)), emma, null, null, null, done(8, "60.0"));
        validate(plan(coachToken, emma, strength, today.minusDays(1)), emma, 8, 4, null, done(8, "60.0"));
        plan(coachToken, emma, strength, today);
        // Hugo : 1 réalisée sur 3, ses non faites sont anciennes.
        plan(coachToken, hugo, strength, today.minusDays(25));
        plan(coachToken, hugo, strength, today.minusDays(20));
        validate(plan(coachToken, hugo, strength, today.minusDays(12)), hugo, 9, 5, null, done(8, "60.0"));
        // Les séances de Claire avec Lucas ne comptent jamais pour le coach.
        plan(claireToken, lucas, claireWorkout, today.minusDays(6));
        validate(plan(claireToken, lucas, claireWorkout, today.minusDays(2)), lucas, 3, 1, null, done(10, "20.0"));
        // Une séance annulée est supprimée : elle ne comptera jamais, même une fois sa date passée.
        String cancelled = plan(coachToken, nathan, strength, today);
        mockMvc.perform(delete(SESSIONS + cancelled).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());

        String kpis = kpis(Map.of());
        assertThat(JsonPath.<String>read(kpis, "$.from")).isEqualTo(today.minusDays(28).toString());
        assertThat(JsonPath.<String>read(kpis, "$.to")).isEqualTo(today.minusDays(1).toString());
        assertThat(JsonPath.<Integer>read(kpis, "$.weeks")).isEqualTo(4);
        assertThat(JsonPath.<List<String>>read(kpis, "$.athletes[*].lastName"))
                .containsExactly("Chevalier", "Fontaine", "Girard", "Lefèvre");
        assertThat(JsonPath.<List<Integer>>read(kpis, "$.athletes[*].doneCount")).containsExactly(0, 1, 2, 3);
        assertThat(JsonPath.<List<Integer>>read(kpis, "$.athletes[*].missedCount")).containsExactly(0, 2, 2, 0);
        assertThat(JsonPath.<List<Integer>>read(kpis, "$.athletes[*].completionRate"))
                .containsExactly(null, 33, 50, 100);
        assertThat(JsonPath.<List<Double>>read(kpis, "$.athletes[*].averageRpe")).containsExactly(null, 9.0, 7.0, 7.5);
        assertThat(JsonPath.<List<Integer>>read(kpis, "$.athletes[*].missedLast7Days")).containsExactly(0, 0, 2, 0);
        assertThat(JsonPath.<List<Boolean>>read(kpis, "$.athletes[*].watch")).containsExactly(false, true, true, false);
        assertThat(JsonPath.<Integer>read(kpis, "$.totals.athleteCount")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(kpis, "$.totals.doneCount")).isEqualTo(6);
        assertThat(JsonPath.<Integer>read(kpis, "$.totals.missedCount")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(kpis, "$.totals.completionRate")).isEqualTo(60);
        assertThat(JsonPath.<Double>read(kpis, "$.totals.averageRpe")).isEqualTo(7.6);
        assertThat(JsonPath.<List<String>>read(kpis, "$.watchlist[*].lastName")).containsExactly("Girard", "Fontaine");
        assertThat(JsonPath.<List<String>>read(kpis, "$.watchlist[0].reasons"))
                .containsExactly("MISSED_RECENTLY", "LOW_COMPLETION");
        assertThat(JsonPath.<List<String>>read(kpis, "$.watchlist[1].reasons")).containsExactly("LOW_COMPLETION");

        // 12 semaines : la séance de Lucas d'il y a 40 jours compte.
        String quarter = kpis(Map.of("weeks", "12"));
        assertThat(JsonPath.<Integer>read(quarter, "$.athletes[2].doneCount")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(quarter, "$.athletes[2].completionRate")).isEqualTo(60);
        assertThat(JsonPath.<Double>read(quarter, "$.athletes[2].averageRpe")).isEqualTo(6.3);
        assertThat(JsonPath.<Integer>read(quarter, "$.totals.completionRate")).isEqualTo(64);
        assertThat(JsonPath.<Double>read(quarter, "$.totals.averageRpe")).isEqualTo(7.2);

        // Groupe : ses membres seulement.
        String group = createGroup("Sprint", lucas, emma);
        String sprint = kpis(Map.of("groupId", group));
        assertThat(JsonPath.<String>read(sprint, "$.groupId")).isEqualTo(group);
        assertThat(JsonPath.<List<String>>read(sprint, "$.athletes[*].lastName")).containsExactly("Girard", "Lefèvre");
        assertThat(JsonPath.<Integer>read(sprint, "$.totals.completionRate")).isEqualTo(71);
        assertThat(JsonPath.<Double>read(sprint, "$.totals.averageRpe")).isEqualTo(7.3);

        // Groupe d'un autre coach, période inconnue.
        String claireGroup = JsonPath.read(mockMvc.perform(json(post("/api/v1/groups"), claireToken,
                        "{\"name\":\"Groupe de Claire\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        expect404(coachToken, "/api/v1/dashboard/kpis?groupId=" + claireGroup, "GROUP_NOT_FOUND");
        mockMvc.perform(get("/api/v1/dashboard/kpis").header(HttpHeaders.AUTHORIZATION, coachToken)
                        .param("weeks", "8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        // Lien clos : l'athlète retiré disparaît du tableau de bord.
        mockMvc.perform(delete("/api/v1/athletes/" + hugo.getId()).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        assertThat(JsonPath.<List<String>>read(kpis(Map.of()), "$.athletes[*].lastName"))
                .containsExactly("Chevalier", "Girard", "Lefèvre");
    }

    private String kpis(Map<String, String> parameters) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/dashboard/kpis")
                .header(HttpHeaders.AUTHORIZATION, coachToken);
        parameters.forEach(request::param);
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String read(String token, String uri) throws Exception {
        return mockMvc.perform(get(uri).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String progressionUri(User athlete, String exerciseId) {
        return "/api/v1/athletes/" + athlete.getId() + "/exercises/" + exerciseId + "/progression";
    }

    private static String recordsUri(User athlete) {
        return "/api/v1/athletes/" + athlete.getId() + "/personal-records";
    }
}
