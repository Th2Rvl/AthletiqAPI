package com.athletiq.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.TestcontainersConfiguration;
import com.athletiq.user.Role;
import com.athletiq.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Parcours de compte sur une vraie base MySQL (Testcontainers) : migration de la table des jetons,
 * création d'un compte coach, réinitialisation du mot de passe et lecture du profil.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccountIntegrationTest {

    private static final String COACH_EMAIL = "claire.martin@club.fr";
    private static final String COACH_PASSWORD = "motdepasse123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from password_reset_token");
        jdbc.update("delete from app_user where email <> ?", "admin@athletiq.test");
    }

    @Test
    void appliesThePasswordResetTokenMigration() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version = '3'",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void createsACoachAccountAndConnectsIt() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" " + COACH_EMAIL + " \",\"password\":\"" + COACH_PASSWORD + "\","
                                + "\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("COACH"))
                .andExpect(jsonPath("$.user.email").value(COACH_EMAIL))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(userRepository.findByEmail(COACH_EMAIL)).isPresent();
        assertThat((String) JsonPath.read(body, "$.accessToken")).isNotBlank();
    }

    @Test
    void refusesASecondAccountWithTheSameEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + COACH_EMAIL + "\",\"password\":\"" + COACH_PASSWORD + "\","
                                + "\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + COACH_EMAIL + "\",\"password\":\"" + COACH_PASSWORD + "\","
                                + "\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void resetsThePasswordWithATokenAndLogsInWithIt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + COACH_EMAIL + "\",\"password\":\"" + COACH_PASSWORD + "\","
                                + "\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isCreated());

        String forgot = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + COACH_EMAIL + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String token = (String) JsonPath.read(forgot, "$.resetToken");
        assertThat(token).asString().isNotBlank();

        // Le jeton n'est jamais stocké en clair : seule son empreinte l'est.
        Integer stored = jdbc.queryForObject(
                "select count(*) from password_reset_token where token_hash = ?", Integer.class, token);
        assertThat(stored).isZero();
        assertThat(jdbc.queryForObject("select count(*) from password_reset_token", Integer.class)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"nouveaumotdepasse1\","
                                + "\"passwordConfirmation\":\"nouveaumotdepasse1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + COACH_EMAIL + "\",\"password\":\"nouveaumotdepasse1\"}"))
                .andExpect(status().isOk());

        // Le jeton est à usage unique.
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"autremotdepasse2\","
                                + "\"passwordConfirmation\":\"autremotdepasse2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void answersTheSameWayForAnUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inconnu@club.fr\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.resetToken").doesNotExist());
    }

    @Test
    void readsAndUpdatesTheProfileOfTheConnectedCoach() throws Exception {
        String token = registerCoach();

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(COACH_EMAIL))
                .andExpect(jsonPath("$.role").value("COACH"));

        mockMvc.perform(patch("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"claire.martin@stade-rennais-athle.fr\",\"firstName\":\"Claire\","
                                + "\"lastName\":\"Martin-Ndiaye\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("claire.martin@stade-rennais-athle.fr"))
                .andExpect(jsonPath("$.lastName").value("Martin-Ndiaye"));

        assertThat(userRepository.findByEmail("claire.martin@stade-rennais-athle.fr")).isPresent();
    }

    @Test
    void refusesTheProfileWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
    }

    private String registerCoach() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + COACH_EMAIL + "\",\"password\":\"" + COACH_PASSWORD + "\","
                                + "\"firstName\":\"Claire\",\"lastName\":\"Martin\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) JsonPath.read(body, "$.accessToken");
    }
}
