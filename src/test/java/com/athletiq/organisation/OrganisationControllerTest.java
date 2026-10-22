package com.athletiq.organisation;

import static com.athletiq.support.TestJwt.as;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.organisation.dto.CreateOrganisationRequest;
import com.athletiq.organisation.dto.InvitationResponse;
import com.athletiq.organisation.dto.OrganisationResponse;
import com.athletiq.organisation.dto.UpdateOrganisationRequest;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrganisationController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class OrganisationControllerTest {

    private static final String BASE = "/api/v1/organisations";
    private static final UUID ORG_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120002");
    private static final UUID OTHER_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120003");
    private static final Instant NOW = Instant.parse("2026-10-22T08:15:30.123456Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganisationService organisationService;

    private static OrganisationResponse rennes() {
        return new OrganisationResponse(ORG_ID, "Stade Rennais Athlétisme", null, 2, 0, 1, NOW, NOW);
    }

    // ---------------------------------------------------------------- sécurité

    @Test
    void refusesAnAnonymousCallerWith401ProblemDetail() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.instance").value(BASE));
        verifyNoInteractions(organisationService);
    }

    @Test
    void refusesAnInvalidTokenWith401() throws Exception {
        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    static Stream<Arguments> allEndpoints() {
        String org = BASE + "/" + ORG_ID;
        return Stream.of(
                Arguments.of(HttpMethod.GET, BASE),
                Arguments.of(HttpMethod.POST, BASE),
                Arguments.of(HttpMethod.GET, org),
                Arguments.of(HttpMethod.PUT, org),
                Arguments.of(HttpMethod.DELETE, org),
                Arguments.of(HttpMethod.GET, org + "/coaches"),
                Arguments.of(HttpMethod.DELETE, org + "/coaches/" + OTHER_ID),
                Arguments.of(HttpMethod.POST, org + "/invitations"),
                Arguments.of(HttpMethod.GET, org + "/invitations"),
                Arguments.of(HttpMethod.GET, org + "/invitations/" + OTHER_ID),
                Arguments.of(HttpMethod.DELETE, org + "/invitations/" + OTHER_ID),
                Arguments.of(HttpMethod.GET, org + "/athletes"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allEndpoints")
    void refusesACoachWith403OnEveryEndpoint(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Club\",\"email\":\"coach@club.fr\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(organisationService);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allEndpoints")
    void refusesAnAthleteWith403OnEveryEndpoint(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri).with(as(Role.ATHLETE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(organisationService);
    }

    // ---------------------------------------------------------------- E1 liste

    @Test
    void listsOrganisationsAsAPage() throws Exception {
        when(organisationService.list(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rennes()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(BASE).with(as(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.content[0].description").value(nullValue()))
                .andExpect(jsonPath("$.content[0].coachCount").value(2))
                .andExpect(jsonPath("$.content[0].pendingInvitationCount").value(1))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-22T08:15:30.123456Z"))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void sortsByNameByDefaultAndCapsThePageSizeAt100() throws Exception {
        when(organisationService.list(eq("rennais"), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get(BASE).param("q", "rennais").param("size", "500").with(as(Role.ADMIN)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(organisationService).list(eq("rennais"), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    void refusesATooLongSearchWith400InvalidParameter() throws Exception {
        mockMvc.perform(get(BASE).param("q", "x".repeat(101)).with(as(Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Paramètre invalide : q"));
        verifyNoInteractions(organisationService);
    }

    // ---------------------------------------------------------------- E2 création

    @Test
    void createsAnOrganisationWith201AndLocation() throws Exception {
        when(organisationService.create(any(CreateOrganisationRequest.class))).thenReturn(rennes());

        mockMvc.perform(post(BASE).with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Stade Rennais Athlétisme  \",\"description\":\"   \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/" + ORG_ID))
                .andExpect(jsonPath("$.name").value("Stade Rennais Athlétisme"));

        verify(organisationService).create(new CreateOrganisationRequest("Stade Rennais Athlétisme", null));
    }

    @Test
    void refusesABlankNameWithOneMessagePerField() throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"description\":\"" + "d".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("La requête contient des champs invalides"))
                .andExpect(jsonPath("$.instance").value(BASE))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Le nom est obligatoire"))
                .andExpect(jsonPath("$.errors[?(@.field == 'description')].message")
                        .value("La description ne doit pas dépasser 500 caractères"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
        verifyNoInteractions(organisationService);
    }

    @Test
    void refusesANameTooShortAfterTrim() throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  A  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Le nom doit contenir entre 2 et 100 caractères"));
    }

    @Test
    void refusesAMalformedBody() throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void returns409WithTheFieldWhenTheNameIsTaken() throws Exception {
        when(organisationService.create(any(CreateOrganisationRequest.class)))
                .thenThrow(OrganisationErrors.nameTaken());

        mockMvc.perform(post(BASE).with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"stade rennais athletisme\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Une organisation porte déjà ce nom"))
                .andExpect(jsonPath("$.instance").value(BASE))
                .andExpect(jsonPath("$.code").value("ORGANISATION_NAME_TAKEN"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    // ---------------------------------------------------------------- E3 à E5

    @Test
    void returns404WhenTheOrganisationDoesNotExist() throws Exception {
        when(organisationService.get(ORG_ID)).thenThrow(OrganisationErrors.organisationNotFound());

        mockMvc.perform(get(BASE + "/" + ORG_ID).with(as(Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANISATION_NOT_FOUND"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }

    @Test
    void refusesAMalformedUuidWith400() throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid").with(as(Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Paramètre invalide : organisationId"));
    }

    @Test
    void updatesAnOrganisation() throws Exception {
        when(organisationService.update(eq(ORG_ID), any(UpdateOrganisationRequest.class))).thenReturn(rennes());

        mockMvc.perform(put(BASE + "/" + ORG_ID).with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stade Rennais Athlétisme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORG_ID.toString()));

        verify(organisationService).update(ORG_ID, new UpdateOrganisationRequest("Stade Rennais Athlétisme", null));
    }

    @Test
    void refusesAnInvalidUpdate() throws Exception {
        mockMvc.perform(put(BASE + "/" + ORG_ID).with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "n".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deletesAnOrganisationWith204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + ORG_ID).with(as(Role.ADMIN)))
                .andExpect(status().isNoContent());
        verify(organisationService).delete(ORG_ID);
    }

    // ---------------------------------------------------------------- E6, E7 coachs

    @Test
    void listsCoachesAsAPage() throws Exception {
        when(organisationService.listCoaches(eq(ORG_ID), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get(BASE + "/" + ORG_ID + "/coaches").with(as(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void removesACoachWith204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + ORG_ID + "/coaches/" + OTHER_ID).with(as(Role.ADMIN)))
                .andExpect(status().isNoContent());
        verify(organisationService).removeCoach(ORG_ID, OTHER_ID);
    }

    @Test
    void returns404WhenTheCoachIsNotMember() throws Exception {
        org.mockito.Mockito.doThrow(OrganisationErrors.coachNotMember())
                .when(organisationService).removeCoach(ORG_ID, OTHER_ID);

        mockMvc.perform(delete(BASE + "/" + ORG_ID + "/coaches/" + OTHER_ID).with(as(Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COACH_NOT_MEMBER"));
    }

    // ---------------------------------------------------------------- E8 à E11 invitations

    @Test
    void invitesACoachWith201AndLocationAfterNormalisingTheEmail() throws Exception {
        UUID invitationId = UUID.randomUUID();
        when(organisationService.inviteCoach(ORG_ID, "coach@club.fr")).thenReturn(new InvitationResponse(
                invitationId, ORG_ID, "coach@club.fr", InvitationStatus.ACCEPTED, OTHER_ID, NOW, NOW));

        mockMvc.perform(post(BASE + "/" + ORG_ID + "/invitations").with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  Coach@Club.FR \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        BASE + "/" + ORG_ID + "/invitations/" + invitationId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.coachId").value(OTHER_ID.toString()));
    }

    @Test
    void refusesAnInvalidEmail() throws Exception {
        mockMvc.perform(post(BASE + "/" + ORG_ID + "/invitations").with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("L'e-mail n'est pas valide"));
        verifyNoInteractions(organisationService);
    }

    @Test
    void refusesAnEmptyEmail() throws Exception {
        mockMvc.perform(post(BASE + "/" + ORG_ID + "/invitations").with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("L'e-mail est obligatoire"));
        verifyNoInteractions(organisationService);
    }

    @Test
    void refusesAnEmailLongerThan254Characters() throws Exception {
        // Adresse syntaxiquement valide (partie locale de 64, labels de 63) mais de 259 caractères :
        // seule la contrainte de longueur doit échouer.
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".fr";
        org.assertj.core.api.Assertions.assertThat(email).hasSizeGreaterThan(254);

        mockMvc.perform(post(BASE + "/" + ORG_ID + "/invitations").with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("L'e-mail ne doit pas dépasser 254 caractères"));
        verifyNoInteractions(organisationService);
    }

    @Test
    void returns409WhenTheAccountIsNotACoach() throws Exception {
        when(organisationService.inviteCoach(ORG_ID, "lucas.girard@athlete.fr"))
                .thenThrow(OrganisationErrors.accountNotCoach());

        mockMvc.perform(post(BASE + "/" + ORG_ID + "/invitations").with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lucas.girard@athlete.fr\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_COACH"))
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void listsPendingInvitationsByDefault() throws Exception {
        when(organisationService.listInvitations(eq(ORG_ID), eq(InvitationStatus.PENDING), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get(BASE + "/" + ORG_ID + "/invitations").with(as(Role.ADMIN)))
                .andExpect(status().isOk());
        verify(organisationService).listInvitations(eq(ORG_ID), eq(InvitationStatus.PENDING), any(Pageable.class));
    }

    @Test
    void refusesAnUnknownInvitationStatus() throws Exception {
        mockMvc.perform(get(BASE + "/" + ORG_ID + "/invitations").param("status", "EXPIRED").with(as(Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Paramètre invalide : status"));
    }

    @Test
    void cancelsAnInvitationWith204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + ORG_ID + "/invitations/" + OTHER_ID).with(as(Role.ADMIN)))
                .andExpect(status().isNoContent());
        verify(organisationService).cancelInvitation(ORG_ID, OTHER_ID);
    }

    @Test
    void returns409WhenTheInvitationIsNoLongerPending() throws Exception {
        org.mockito.Mockito.doThrow(OrganisationErrors.invitationNotPending())
                .when(organisationService).cancelInvitation(ORG_ID, OTHER_ID);

        mockMvc.perform(delete(BASE + "/" + ORG_ID + "/invitations/" + OTHER_ID).with(as(Role.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
    }

    // ---------------------------------------------------------------- E12 athlètes

    @Test
    void listsAthletesAsAnEmptyPage() throws Exception {
        when(organisationService.listAthletes(eq(ORG_ID), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get(BASE + "/" + ORG_ID + "/athletes").with(as(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    // ---------------------------------------------------------------- erreurs imprévues

    @Test
    void hidesTheStackTraceOfAnUnexpectedError() throws Exception {
        when(organisationService.get(ORG_ID)).thenThrow(new IllegalStateException("secret interne"));

        mockMvc.perform(get(BASE + "/" + ORG_ID).with(as(Role.ADMIN)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Erreur interne"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
