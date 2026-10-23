package com.athletiq.athlete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.TestcontainersConfiguration;
import com.athletiq.group.AthleteGroup;
import com.athletiq.group.AthleteGroupMember;
import com.athletiq.group.AthleteGroupMemberRepository;
import com.athletiq.group.AthleteGroupRepository;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserRepository;
import com.athletiq.user.UserService;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Suivi des athlètes et groupes sur une vraie base MySQL 8.4 (Testcontainers) : migration V4 validée
 * par Hibernate ({@code ddl-auto=validate}, VARBINARY et colonne générée), contraintes d'unicité,
 * cascade, consommation concurrente d'un code, puis parcours HTTP complet avec de vrais JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AthleteGroupIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@athletiq.test";
    private static final String PASSWORD = "mot-de-passe-123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoachAthleteRepository linkRepository;
    @Autowired
    private CoachInvitationRepository invitationRepository;
    @Autowired
    private AthleteGroupRepository groupRepository;
    @Autowired
    private AthleteGroupMemberRepository memberRepository;
    @Autowired
    private InvitationCodeCipher cipher;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from athlete_group_member");
        jdbc.update("delete from athlete_group");
        jdbc.update("delete from coach_athlete");
        jdbc.update("delete from coach_invitation");
        jdbc.update("delete from app_user where email <> ?", ADMIN_EMAIL);
    }

    // ---------------------------------------------------------------- schéma et contraintes

    @Test
    void appliesMigrationV4() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version = '4'", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void onlyOneActiveLinkPerCoachAndAthleteButARelinkAfterEndIsAccepted() {
        User coach = coach("coach@club.fr");
        User athlete = athlete("lucas.girard@athlete.fr");
        CoachAthlete first = linkRepository.saveAndFlush(link(coach, athlete));

        assertThatThrownBy(() -> linkRepository.saveAndFlush(link(coach, athlete)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(InvitationService.UK_COACH_ATHLETE_ACTIVE);

        first.setEndedAt(Instant.now());
        linkRepository.saveAndFlush(first);
        linkRepository.saveAndFlush(link(coach, athlete));
        assertThat(linkRepository.count()).isEqualTo(2);
        assertThat(linkRepository.existsByCoachIdAndAthleteIdAndEndedAtIsNull(coach.getId(), athlete.getId())).isTrue();
    }

    @Test
    void groupNameIsUniquePerCoachIgnoringCaseAndAccents() {
        User coach = coach("coach@club.fr");
        User other = coach("claire.martin@club.fr");
        groupRepository.saveAndFlush(AthleteGroup.builder().coach(coach).name("Sprint").build());

        for (String duplicate : List.of("sprint", "Sprínt", "SPRINT")) {
            assertThatThrownBy(() -> groupRepository.saveAndFlush(
                    AthleteGroup.builder().coach(coach).name(duplicate).build()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_athlete_group_name");
        }
        assertThat(groupRepository.existsByCoachIdAndName(coach.getId(), "sprínt")).isTrue();
        groupRepository.saveAndFlush(AthleteGroup.builder().coach(other).name("Sprint").build());
    }

    @Test
    void deletingAGroupCascadesToMembersOnly() {
        User coach = coach("coach@club.fr");
        User athlete = athlete("lucas.girard@athlete.fr");
        linkRepository.saveAndFlush(link(coach, athlete));
        AthleteGroup group = groupRepository.saveAndFlush(AthleteGroup.builder().coach(coach).name("Sprint").build());
        memberRepository.saveAndFlush(AthleteGroupMember.builder()
                .group(group).athlete(athlete).addedAt(Instant.now()).build());

        jdbc.update("delete from athlete_group");

        assertThat(memberRepository.count()).isZero();
        assertThat(linkRepository.count()).isEqualTo(1);
        assertThat(userRepository.existsByEmail("lucas.girard@athlete.fr")).isTrue();
    }

    @Test
    void aCodeEnteredTwiceAtTheSameTimeHasOnlyOneWinner() throws Exception {
        User coach = coach("coach@club.fr");
        User lucas = athlete("lucas.girard@athlete.fr");
        User emma = athlete("emma.lefevre@athlete.fr");
        String hash = cipher.hash("K7QH3MPA");
        invitationRepository.saveAndFlush(CoachInvitation.builder()
                .coach(coach).label("Lucas").codeEncrypted(cipher.encrypt("K7QH3MPA", coach.getId()))
                .codeHash(hash).expiresAt(Instant.now().plus(Duration.ofDays(7))).build());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (User athlete : List.of(lucas, emma)) {
                Callable<Integer> consume = () -> {
                    start.await();
                    return transaction.execute(status -> invitationRepository.consume(hash,
                            userRepository.getReferenceById(athlete.getId()), Instant.now()));
                };
                results.add(executor.submit(consume));
            }
            start.countDown();
            int total = 0;
            for (Future<Integer> result : results) {
                total += result.get();
            }
            assertThat(total).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- parcours HTTP

    @Test
    void coachInvitesFollowsAndGroupsAthletesEndToEnd() throws Exception {
        coach("coach@club.fr");
        coach("claire.martin@club.fr");
        User lucas = athlete("lucas.girard@athlete.fr", "Lucas", "Girard");
        User emma = athlete("emma.lefevre@athlete.fr", "Émma", "Lefèvre");
        User other = athlete("hugo.fontaine@athlete.fr", "Hugo", "Fontaine");
        String coachToken = login("coach@club.fr");
        String claireToken = login("claire.martin@club.fr");
        String lucasToken = login("lucas.girard@athlete.fr");
        String emmaToken = login("emma.lefevre@athlete.fr");
        String hugoToken = login("hugo.fontaine@athlete.fr");

        // US10 : création, liste avec le code déchiffré, code jamais stocké en clair
        String lucasCode = createInvitation(coachToken, "Lucas Girard");
        String emmaCode = createInvitation(coachToken, "Emma Lefèvre");
        mockMvc.perform(get("/api/v1/invitations").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code").value(containsInAnyOrder(emmaCode, lucasCode)));
        for (java.util.Map<String, Object> row : jdbc.queryForList("select code_hash, code_encrypted from coach_invitation")) {
            assertThat((String) row.get("code_hash")).hasSize(64).isNotEqualTo(lucasCode);
            assertThat(new String((byte[]) row.get("code_encrypted"), java.nio.charset.StandardCharsets.ISO_8859_1))
                    .doesNotContain(lucasCode).doesNotContain(emmaCode);
        }
        mockMvc.perform(post("/api/v1/me/coaches").header(HttpHeaders.AUTHORIZATION, coachToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + lucasCode + "\"}"))
                .andExpect(status().isForbidden());

        // Saisie par les athlètes, en minuscules et avec tiret
        String typed = (lucasCode.substring(0, 4) + "-" + lucasCode.substring(4)).toLowerCase();
        mockMvc.perform(json(post("/api/v1/me/coaches"), lucasToken, "{\"code\":\"" + typed + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/me/coaches"))
                .andExpect(jsonPath("$.lastName").value("Petit"));
        mockMvc.perform(json(post("/api/v1/me/coaches"), hugoToken, "{\"code\":\"" + lucasCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_CODE_INVALID"));
        // Déjà suivi : 409, et le code d'Emma n'est pas consommé
        mockMvc.perform(json(post("/api/v1/me/coaches"), lucasToken, "{\"code\":\"" + emmaCode + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATHLETE_ALREADY_LINKED"));
        mockMvc.perform(json(post("/api/v1/me/coaches"), emmaToken, "{\"code\":\"" + emmaCode + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/invitations").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // Révocation : le code révoqué est refusé avec le même code d'erreur
        String revoked = mockMvc.perform(json(post("/api/v1/invitations"), coachToken, "{\"label\":\"Hugo\"}"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(delete("/api/v1/invitations/" + JsonPath.read(revoked, "$.id"))
                        .header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(json(post("/api/v1/me/coaches"), hugoToken,
                        "{\"code\":\"" + JsonPath.read(revoked, "$.code") + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_CODE_INVALID"));
        mockMvc.perform(delete("/api/v1/invitations/" + JsonPath.read(revoked, "$.id"))
                        .header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));

        // Lucas est aussi suivi par Claire (RM2)
        String claireCode = createInvitation(claireToken, "Lucas");
        mockMvc.perform(json(post("/api/v1/me/coaches"), lucasToken, "{\"code\":\"" + claireCode + "\"}"))
                .andExpect(status().isCreated());

        // US11 : liste triée, recherche insensible casse et accents, isolement entre coachs
        mockMvc.perform(get("/api/v1/athletes").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Girard", "Lefèvre")))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/v1/athletes").param("q", "LEFEVRE").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[*].email").value(contains("emma.lefevre@athlete.fr")));
        mockMvc.perform(get("/api/v1/athletes").param("q", "emma").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/v1/athletes").param("q", "%").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/api/v1/athletes").header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Girard")));

        // US13 : groupes, nom unique à la casse et aux accents près
        String sprintId = createGroup(coachToken, " Sprint ");
        String fondId = createGroup(coachToken, "Demi-fond");
        mockMvc.perform(json(post("/api/v1/groups"), coachToken, "{\"name\":\"sprínt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_NAME_ALREADY_EXISTS"));
        mockMvc.perform(json(patch("/api/v1/groups/" + fondId), coachToken, "{\"name\":\"SPRINT\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(json(patch("/api/v1/groups/" + sprintId), coachToken, "{\"name\":\"SPRINT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SPRINT"));
        createGroup(claireToken, "Sprint");
        mockMvc.perform(get("/api/v1/groups/" + sprintId).header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));

        // US14 : ajout par lot atomique, idempotent, filtres groupId / excludeGroupId
        mockMvc.perform(json(post("/api/v1/groups/" + sprintId + "/athletes"), coachToken,
                        "{\"athleteIds\":[\"" + lucas.getId() + "\",\"" + other.getId() + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));
        assertThat(memberRepository.count()).isZero();
        mockMvc.perform(json(post("/api/v1/groups/" + sprintId + "/athletes"), coachToken,
                        "{\"athleteIds\":[\"" + lucas.getId() + "\",\"" + emma.getId() + "\",\"" + lucas.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteCount").value(2));
        mockMvc.perform(json(post("/api/v1/groups/" + sprintId + "/athletes"), coachToken,
                        "{\"athleteIds\":[\"" + lucas.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteCount").value(2));
        mockMvc.perform(json(post("/api/v1/groups/" + fondId + "/athletes"), coachToken,
                        "{\"athleteIds\":[\"" + lucas.getId() + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[*].name").value(contains("Demi-fond", "SPRINT")))
                .andExpect(jsonPath("$.content[*].athleteCount").value(contains(1, 2)));
        mockMvc.perform(get("/api/v1/athletes").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[0].groups[*].name").value(contains("Demi-fond", "SPRINT")));
        mockMvc.perform(get("/api/v1/athletes").header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(jsonPath("$.content[0].groups").value(empty()));
        mockMvc.perform(get("/api/v1/athletes").param("groupId", fondId).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Girard")));
        mockMvc.perform(get("/api/v1/athletes").param("excludeGroupId", fondId)
                        .header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Lefèvre")));
        mockMvc.perform(get("/api/v1/athletes").param("groupId", sprintId).header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/groups/" + sprintId + "/athletes/" + emma.getId())
                        .header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/groups/" + sprintId + "/athletes/" + emma.getId())
                        .header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));

        // US12 : retrait de Lucas, retiré des groupes de ce coach, toujours suivi par Claire
        mockMvc.perform(delete("/api/v1/athletes/" + lucas.getId()).header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/athletes/" + lucas.getId()).header(HttpHeaders.AUTHORIZATION, claireToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/groups/" + sprintId + "/athletes").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Girard")));
        mockMvc.perform(delete("/api/v1/athletes/" + lucas.getId()).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[*].athleteCount").value(contains(0, 0)));
        mockMvc.perform(get("/api/v1/athletes").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Lefèvre")));
        assertThat(userRepository.existsByEmail("lucas.girard@athlete.fr")).isTrue();

        // Ré-invitation : un nouveau lien, l'ancien reste clôturé
        String again = createInvitation(coachToken, "Lucas, retour");
        mockMvc.perform(json(post("/api/v1/me/coaches"), lucasToken, "{\"code\":\"" + again + "\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from coach_athlete where athlete_id = ?",
                Integer.class, uuidBytes(lucas))).isEqualTo(3);

        // RM9 : suppression d'un groupe, athlètes toujours suivis
        mockMvc.perform(delete("/api/v1/groups/" + fondId).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/groups/" + fondId).header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/athletes").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    // ---------------------------------------------------------------- outils

    private User coach(String email) {
        return userService.create(email, PASSWORD, "Nicolas", "Petit", Role.COACH);
    }

    private User athlete(String email) {
        return athlete(email, "Lucas", "Girard");
    }

    private User athlete(String email, String firstName, String lastName) {
        return userService.create(email, PASSWORD, firstName, lastName, Role.ATHLETE);
    }

    private static CoachAthlete link(User coach, User athlete) {
        return CoachAthlete.builder().coach(coach).athlete(athlete).joinedAt(Instant.now()).build();
    }

    private String createInvitation(String token, String label) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/invitations"), token, "{\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private String createGroup(String token, String name) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/groups"), token, "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.athleteCount").value(0))
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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token,
                                                      String body) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, token);
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static byte[] uuidBytes(User user) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(user.getId().getMostSignificantBits())
                .putLong(user.getId().getLeastSignificantBits())
                .array();
    }
}
