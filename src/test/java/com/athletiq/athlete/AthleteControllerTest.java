package com.athletiq.athlete;

import static com.athletiq.support.TestJwt.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.athlete.dto.AthleteResponse;
import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.athlete.dto.CreateInvitationRequest;
import com.athletiq.athlete.dto.GroupRef;
import com.athletiq.athlete.dto.InvitationResponse;
import com.athletiq.athlete.dto.RedeemInvitationRequest;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.group.GroupErrors;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Invitations, liste et retrait des athlètes, saisie d'un code : codes HTTP, validation et rôles. */
@WebMvcTest({AthleteController.class, InvitationController.class, CoachLinkController.class})
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AthleteControllerTest {

    private static final UUID ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120002");
    private static final UUID GROUP_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120003");
    private static final Instant NOW = Instant.parse("2026-10-23T08:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AthleteService athleteService;
    @MockitoBean
    private InvitationService invitationService;
    @MockitoBean
    private MyCoachesService myCoachesService;

    // ---------------------------------------------------------------- sécurité

    static Stream<Arguments> coachEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/athletes"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/athletes/" + ID),
                Arguments.of(HttpMethod.GET, "/api/v1/invitations"),
                Arguments.of(HttpMethod.POST, "/api/v1/invitations"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/invitations/" + ID));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("coachEndpoints")
    void coachEndpointsRefuseAnonymousAdminAndAthlete(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
            mockMvc.perform(request(method, uri).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Lucas\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verifyNoInteractions(athleteService, invitationService);
    }

    @Test
    void redeemRefusesAnonymousCoachAndAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/me/coaches").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"K7QH3MPA\"}"))
                .andExpect(status().isUnauthorized());
        for (Role role : List.of(Role.COACH, Role.ADMIN)) {
            mockMvc.perform(post("/api/v1/me/coaches").with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"K7QH3MPA\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verifyNoInteractions(invitationService);
    }

    // ---------------------------------------------------------------- GET /athletes

    @Test
    void listsAthletesAsAPage() throws Exception {
        AthleteResponse lucas = new AthleteResponse(ID, "Lucas", "Girard", "lucas.girard@athlete.fr", NOW,
                List.of(new GroupRef(GROUP_ID, "Sprint")));
        when(athleteService.list(eq("mar"), eq(GROUP_ID), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lucas), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/v1/athletes").with(as(Role.COACH))
                        .param("q", "mar").param("groupId", GROUP_ID.toString())
                        .param("page", "1").param("size", "5").param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.content[0].email").value("lucas.girard@athlete.fr"))
                .andExpect(jsonPath("$.content[0].joinedAt").value("2026-10-23T08:15:30Z"))
                .andExpect(jsonPath("$.content[0].groups[0].name").value("Sprint"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(6));
        verify(athleteService).list("mar", GROUP_ID, null, PageRequest.of(1, 5));
    }

    @Test
    void refusesAPageSizeAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/athletes").with(as(Role.COACH)).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(athleteService);
    }

    @Test
    void refusesASearchLongerThan100AndAMalformedGroupId() throws Exception {
        mockMvc.perform(get("/api/v1/athletes").with(as(Role.COACH)).param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/v1/athletes").with(as(Role.COACH)).param("groupId", "pas-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void passesOnServiceErrorsAsProblemDetail() throws Exception {
        when(athleteService.list(any(), any(), any(), any())).thenThrow(new InvalidParameterException("excludeGroupId"));
        mockMvc.perform(get("/api/v1/athletes").with(as(Role.COACH))
                        .param("groupId", GROUP_ID.toString()).param("excludeGroupId", ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        doThrow(GroupErrors.groupNotFound()).when(athleteService).list(any(), any(), any(), any());
        mockMvc.perform(get("/api/v1/athletes").with(as(Role.COACH)).param("groupId", GROUP_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- DELETE /athletes/{id}

    @Test
    void removesAnAthlete() throws Exception {
        mockMvc.perform(delete("/api/v1/athletes/" + ID).with(as(Role.COACH)))
                .andExpect(status().isNoContent());
        verify(athleteService).remove(ID);
    }

    @Test
    void removingAnAthleteNotFollowedAnswers404() throws Exception {
        doThrow(AthleteErrors.athleteNotFound()).when(athleteService).remove(ID);

        mockMvc.perform(delete("/api/v1/athletes/" + ID).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- invitations

    @Test
    void createsAnInvitationWithItsLocation() throws Exception {
        when(invitationService.create(new CreateInvitationRequest("Lucas Girard")))
                .thenReturn(new InvitationResponse(ID, "Lucas Girard", "K7QH3MPA", NOW, NOW));

        mockMvc.perform(post("/api/v1/invitations").with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"  Lucas Girard \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/invitations/" + ID))
                .andExpect(jsonPath("$.code").value("K7QH3MPA"))
                .andExpect(jsonPath("$.label").value("Lucas Girard"));
    }

    @Test
    void refusesAnInvitationWithoutLabel() throws Exception {
        for (String body : List.of("{}", "{\"label\":\"   \"}", "{\"label\":\"" + "x".repeat(101) + "\"}")) {
            mockMvc.perform(post("/api/v1/invitations").with(as(Role.COACH))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("label"));
        }
        verifyNoInteractions(invitationService);
    }

    @Test
    void listsAndRevokesInvitations() throws Exception {
        when(invitationService.listPending(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(new InvitationResponse(ID, "Lucas", "K7QH3MPA", NOW, NOW))));

        mockMvc.perform(get("/api/v1/invitations").with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("K7QH3MPA"));
        mockMvc.perform(delete("/api/v1/invitations/" + ID).with(as(Role.COACH)))
                .andExpect(status().isNoContent());

        doThrow(AthleteErrors.invitationNotFound()).when(invitationService).revoke(ID);
        mockMvc.perform(delete("/api/v1/invitations/" + ID).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- POST /me/coaches

    @Test
    void redeemsACode() throws Exception {
        when(invitationService.redeem(new RedeemInvitationRequest("k7qh-3mpa")))
                .thenReturn(new CoachLinkResponse(ID, "Nicolas", "Petit", NOW));

        mockMvc.perform(post("/api/v1/me/coaches").with(as(Role.ATHLETE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"k7qh-3mpa\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/me/coaches"))
                .andExpect(jsonPath("$.coachId").value(ID.toString()))
                .andExpect(jsonPath("$.lastName").value("Petit"));
    }

    @Test
    void redeemErrors() throws Exception {
        mockMvc.perform(post("/api/v1/me/coaches").with(as(Role.ATHLETE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("code"));

        when(invitationService.redeem(any())).thenThrow(AthleteErrors.invitationCodeInvalid());
        mockMvc.perform(post("/api/v1/me/coaches").with(as(Role.ATHLETE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ZZZZZZZZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_CODE_INVALID"))
                .andExpect(jsonPath("$.field").value("code"));

        doThrow(AthleteErrors.athleteAlreadyLinked()).when(invitationService).redeem(any());
        mockMvc.perform(post("/api/v1/me/coaches").with(as(Role.ATHLETE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"K7QH3MPA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATHLETE_ALREADY_LINKED"));
    }

    // ---------------------------------------------------------------- GET et DELETE /me/coaches

    @Test
    void myCoachesEndpointsRefuseAnonymousCoachAndAdmin() throws Exception {
        for (String uri : List.of("/api/v1/me/coaches", "/api/v1/me/coaches/" + ID)) {
            HttpMethod method = uri.endsWith(ID.toString()) ? HttpMethod.DELETE : HttpMethod.GET;
            mockMvc.perform(request(method, uri)).andExpect(status().isUnauthorized());
            for (Role role : List.of(Role.COACH, Role.ADMIN)) {
                mockMvc.perform(request(method, uri).with(as(role)))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }
        verifyNoInteractions(myCoachesService);
    }

    @Test
    void listsMyCoaches() throws Exception {
        when(myCoachesService.list()).thenReturn(List.of(new CoachLinkResponse(ID, "Nicolas", "Petit", NOW)));

        mockMvc.perform(get("/api/v1/me/coaches").with(as(Role.ATHLETE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coachId").value(ID.toString()))
                .andExpect(jsonPath("$[0].firstName").value("Nicolas"));
    }

    @Test
    void listsNoCoachForAnAthleteWithoutCoach() throws Exception {
        when(myCoachesService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/me/coaches").with(as(Role.ATHLETE)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void leavesACoach() throws Exception {
        mockMvc.perform(delete("/api/v1/me/coaches/" + ID).with(as(Role.ATHLETE)))
                .andExpect(status().isNoContent());

        verify(myCoachesService).leave(ID);
    }

    @Test
    void leavingAnUnknownCoachIsA404() throws Exception {
        doThrow(AthleteErrors.coachLinkNotFound()).when(myCoachesService).leave(ID);

        mockMvc.perform(delete("/api/v1/me/coaches/" + ID).with(as(Role.ATHLETE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COACH_LINK_NOT_FOUND"));
    }
}
