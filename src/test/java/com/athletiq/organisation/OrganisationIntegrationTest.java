package com.athletiq.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.TestcontainersConfiguration;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserRepository;
import com.athletiq.user.UserService;
import com.jayway.jsonpath.JsonPath;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
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
 * Tests sur une vraie base MySQL 8.4 (Testcontainers) : migrations Flyway validées par Hibernate,
 * contraintes d'unicité et cascades, puis parcours HTTP complet avec un vrai JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrganisationIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@athletiq.test";
    private static final String ADMIN_PASSWORD = "test-admin-password";
    private static final String COACH_PASSWORD = "coach-password-123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganisationRepository organisationRepository;
    @Autowired
    private OrganisationCoachRepository coachRepository;
    @Autowired
    private OrganisationInvitationRepository invitationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private OrganisationService organisationService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from organisation_invitation");
        jdbc.update("delete from organisation_coach");
        jdbc.update("delete from organisation");
        jdbc.update("delete from app_user where email <> ?", ADMIN_EMAIL);
    }

    // ---------------------------------------------------------------- schéma

    @Test
    void appliesMigrationsV1AndV2() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version in ('1', '2')",
                Integer.class);
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void createsTheBootstrapAdmin() {
        assertThat(userRepository.findByEmail(ADMIN_EMAIL)).get()
                .extracting(User::getRole).isEqualTo(Role.ADMIN);
    }

    @Test
    void uniqueNameIgnoresCaseAndAccents() {
        organisationRepository.saveAndFlush(Organisation.builder().name("Stade Rennais Athlétisme").build());

        assertThat(organisationRepository.existsByName("stade rennais athletisme")).isTrue();
        assertThatThrownBy(() -> organisationRepository.saveAndFlush(
                Organisation.builder().name("stade rennais athletisme").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(OrganisationService.UK_ORGANISATION_NAME);
    }

    @Test
    void aCoachIsMemberOnlyOnceOfAnOrganisation() {
        Organisation organisation = organisationRepository.saveAndFlush(Organisation.builder().name("Club").build());
        User coach = userService.create("coach@club.fr", COACH_PASSWORD, "Nicolas", "Petit", Role.COACH);
        coachRepository.saveAndFlush(membership(organisation, coach));

        assertThatThrownBy(() -> coachRepository.saveAndFlush(membership(organisation, coach)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(OrganisationService.UK_ORGANISATION_COACH);
    }

    @Test
    void onlyOnePendingInvitationPerEmailAndOrganisation() {
        Organisation organisation = organisationRepository.saveAndFlush(Organisation.builder().name("Club").build());
        invitationRepository.saveAndFlush(invitation(organisation, InvitationStatus.CANCELLED));
        invitationRepository.saveAndFlush(invitation(organisation, InvitationStatus.CANCELLED));
        invitationRepository.saveAndFlush(invitation(organisation, InvitationStatus.PENDING));

        assertThatThrownBy(() -> invitationRepository.saveAndFlush(invitation(organisation, InvitationStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(OrganisationService.UK_INVITATION_PENDING);
    }

    @Test
    void deletingAnOrganisationInDatabaseCascadesButKeepsAccounts() {
        Organisation organisation = organisationRepository.saveAndFlush(Organisation.builder().name("Club").build());
        User coach = userService.create("coach@club.fr", COACH_PASSWORD, "Nicolas", "Petit", Role.COACH);
        coachRepository.saveAndFlush(membership(organisation, coach));
        invitationRepository.saveAndFlush(invitation(organisation, InvitationStatus.PENDING));

        jdbc.update("delete from organisation where id = ?", uuidToBytes(organisation.getId()));

        assertThat(coachRepository.count()).isZero();
        assertThat(invitationRepository.count()).isZero();
        assertThat(userRepository.existsByEmail("coach@club.fr")).isTrue();
    }

    @Test
    void convertsPendingInvitationsWhenTheCoachAccountIsCreated() {
        Organisation organisation = organisationRepository.saveAndFlush(Organisation.builder().name("Club").build());
        invitationRepository.saveAndFlush(OrganisationInvitation.builder()
                .organisation(organisation).email("nouveau@club.fr").status(InvitationStatus.PENDING).build());
        User coach = userService.create("nouveau@club.fr", COACH_PASSWORD, "Léa", "Nouvelle", Role.COACH);

        int created = new TransactionTemplate(transactionManager)
                .execute(status -> organisationService.convertPendingInvitations(coach));

        assertThat(created).isEqualTo(1);
        assertThat(coachRepository.existsByOrganisationIdAndCoachId(organisation.getId(), coach.getId())).isTrue();
        assertThat(invitationRepository.countByOrganisationIdAndStatus(organisation.getId(), InvitationStatus.ACCEPTED))
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------- parcours HTTP

    @Test
    void adminManagesOrganisationsEndToEnd() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        User coach = userService.create("coach@club.fr", COACH_PASSWORD, "Nicolas", "Petit", Role.COACH);
        userService.create("lucas.girard@athlete.fr", COACH_PASSWORD, "Lucas", "Girard", Role.ATHLETE);

        // US01 : création, puis refus du même nom écrit autrement
        String created = mockMvc.perform(json(post("/api/v1/organisations"), admin,
                        "{\"name\":\" Stade Rennais Athlétisme \",\"description\":\"Sprint\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Stade Rennais Athlétisme"))
                .andExpect(jsonPath("$.coachCount").value(0))
                .andReturn().getResponse().getContentAsString();
        String organisationId = JsonPath.read(created, "$.id");
        String base = "/api/v1/organisations/" + organisationId;

        mockMvc.perform(json(post("/api/v1/organisations"), admin, "{\"name\":\"  stade rennais athletisme \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANISATION_NAME_TAKEN"))
                .andExpect(jsonPath("$.field").value("name"));

        // US03 : coach existant rattaché, e-mail inconnu en attente, doublon refusé, compte non coach refusé
        mockMvc.perform(json(post(base + "/invitations"), admin, "{\"email\":\" Coach@Club.fr \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.coachId").value(coach.getId().toString()));
        mockMvc.perform(json(post(base + "/invitations"), admin, "{\"email\":\"coach@club.fr\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COACH_ALREADY_MEMBER"));
        String pending = mockMvc.perform(json(post(base + "/invitations"), admin, "{\"email\":\"nouveau@club.fr\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.coachId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(json(post(base + "/invitations"), admin, "{\"email\":\"nouveau@club.fr\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));
        mockMvc.perform(json(post(base + "/invitations"), admin, "{\"email\":\"lucas.girard@athlete.fr\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_COACH"));

        // US05 : fiche avec compteurs, liste des coachs, invitations en attente, athlètes vides
        mockMvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachCount").value(1))
                .andExpect(jsonPath("$.pendingInvitationCount").value(1))
                .andExpect(jsonPath("$.athleteCount").value(0));
        mockMvc.perform(get("/api/v1/organisations").param("q", "RENNAIS").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].coachCount").value(1))
                .andExpect(jsonPath("$.content[0].pendingInvitationCount").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get(base + "/coaches").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("coach@club.fr"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mockMvc.perform(get(base + "/invitations").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("nouveau@club.fr"));
        mockMvc.perform(get(base + "/athletes").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // Annulation d'invitation, puis refus d'une seconde annulation
        String invitationPath = base + "/invitations/" + JsonPath.read(pending, "$.id");
        mockMvc.perform(delete(invitationPath).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(invitationPath).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));

        // Un coach authentifié n'a pas accès
        String coachToken = login("coach@club.fr", COACH_PASSWORD);
        mockMvc.perform(get("/api/v1/organisations").header(HttpHeaders.AUTHORIZATION, coachToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // US04 puis US02 : retrait du coach, suppression de l'organisation, comptes conservés
        mockMvc.perform(delete(base + "/coaches/" + coach.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(base + "/coaches/" + coach.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COACH_NOT_MEMBER"));
        mockMvc.perform(delete(base).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(base).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANISATION_NOT_FOUND"));
        assertThat(invitationRepository.count()).isZero();
        assertThat(userRepository.existsByEmail("coach@club.fr")).isTrue();
    }

    @Test
    void deletingAnOrganisationThroughTheApiRemovesMembershipsAndInvitations() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Organisation organisation = organisationRepository.saveAndFlush(Organisation.builder().name("Club").build());
        User coach = userService.create("coach@club.fr", COACH_PASSWORD, "Nicolas", "Petit", Role.COACH);
        coachRepository.saveAndFlush(membership(organisation, coach));
        invitationRepository.saveAndFlush(invitation(organisation, InvitationStatus.PENDING));

        mockMvc.perform(delete("/api/v1/organisations/" + organisation.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(organisationRepository.existsById(organisation.getId())).isFalse();
        assertThat(coachRepository.count()).isZero();
        assertThat(invitationRepository.count()).isZero();
        assertThat(userRepository.existsByEmail("coach@club.fr")).isTrue();
    }

    @Test
    void listsCoachesSortedByLastNameThenFirstName() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Organisation organisation = organisationRepository.saveAndFlush(Organisation.builder().name("Club").build());
        // Insérés volontairement dans le désordre
        for (String[] coach : new String[][] {
                {"zoe.martin@club.fr", "Zoé", "Martin"},
                {"bruno.bernard@club.fr", "Bruno", "Bernard"},
                {"alice.martin@club.fr", "Alice", "Martin"},
                {"camille.dubois@club.fr", "Camille", "Dubois"},
                {"anne.bernard@club.fr", "Anne", "Bernard"}}) {
            User user = userService.create(coach[0], COACH_PASSWORD, coach[1], coach[2], Role.COACH);
            coachRepository.saveAndFlush(membership(organisation, user));
        }

        mockMvc.perform(get("/api/v1/organisations/" + organisation.getId() + "/coaches")
                        .param("sort", "email,desc")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].lastName")
                        .value(org.hamcrest.Matchers.contains("Bernard", "Bernard", "Dubois", "Martin", "Martin")))
                .andExpect(jsonPath("$.content[*].firstName")
                        .value(org.hamcrest.Matchers.contains("Anne", "Bruno", "Camille", "Alice", "Zoé")));
    }

    @Test
    void refusesAWrongPassword() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/login"), null,
                        "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"mauvais-mot-de-passe\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ---------------------------------------------------------------- outils

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(json(post("/api/v1/auth/login"), null,
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
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

    private static OrganisationCoach membership(Organisation organisation, User coach) {
        return OrganisationCoach.builder().organisation(organisation).coach(coach).joinedAt(Instant.now()).build();
    }

    private static OrganisationInvitation invitation(Organisation organisation, InvitationStatus status) {
        return OrganisationInvitation.builder()
                .organisation(organisation).email("nouveau@club.fr").status(status).build();
    }

    private static byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
