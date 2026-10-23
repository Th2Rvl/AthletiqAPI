package com.athletiq.group;

import static com.athletiq.support.TestJwt.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.group.dto.CreateGroupRequest;
import com.athletiq.group.dto.GroupMemberResponse;
import com.athletiq.group.dto.GroupResponse;
import com.athletiq.group.dto.RenameGroupRequest;
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

@WebMvcTest(GroupController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class GroupControllerTest {

    private static final String BASE = "/api/v1/groups";
    private static final UUID GROUP_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120002");
    private static final UUID ATHLETE_ID = UUID.fromString("1f0b0c3e-7a5d-6b2e-9c41-0242ac120003");
    private static final Instant NOW = Instant.parse("2026-10-23T08:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;
    @MockitoBean
    private GroupMemberService memberService;

    private static GroupResponse sprint(long count) {
        return new GroupResponse(GROUP_ID, "Sprint", count, NOW);
    }

    static Stream<Arguments> allEndpoints() {
        String group = BASE + "/" + GROUP_ID;
        return Stream.of(
                Arguments.of(HttpMethod.GET, BASE),
                Arguments.of(HttpMethod.POST, BASE),
                Arguments.of(HttpMethod.GET, group),
                Arguments.of(HttpMethod.PATCH, group),
                Arguments.of(HttpMethod.DELETE, group),
                Arguments.of(HttpMethod.GET, group + "/athletes"),
                Arguments.of(HttpMethod.POST, group + "/athletes"),
                Arguments.of(HttpMethod.DELETE, group + "/athletes/" + ATHLETE_ID));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allEndpoints")
    void refusesAnonymousAdminAndAthlete(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized());
        for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
            mockMvc.perform(request(method, uri).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sprint\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verifyNoInteractions(groupService, memberService);
    }

    @Test
    void listsGroups() throws Exception {
        when(groupService.list(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(sprint(3))));

        mockMvc.perform(get(BASE).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Sprint"))
                .andExpect(jsonPath("$.content[0].athleteCount").value(3))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get(BASE).with(as(Role.COACH)).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void createsAGroupWithItsLocation() throws Exception {
        when(groupService.create(new CreateGroupRequest("Sprint"))).thenReturn(sprint(0));

        mockMvc.perform(post(BASE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Sprint \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/" + GROUP_ID))
                .andExpect(jsonPath("$.athleteCount").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"    \"}",
            "{\"name\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"})
    void refusesAnInvalidName(String body) throws Exception {
        mockMvc.perform(post(BASE).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        mockMvc.perform(patch(BASE + "/" + GROUP_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verifyNoInteractions(groupService);
    }

    @Test
    void acceptsSixtyCharactersAfterTrim() throws Exception {
        String name = "a".repeat(60);
        when(groupService.create(new CreateGroupRequest(name))).thenReturn(sprint(0));

        mockMvc.perform(post(BASE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  " + name + "  \"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateNameAnswers409WithTheField() throws Exception {
        when(groupService.create(any())).thenThrow(GroupErrors.nameAlreadyExists());

        mockMvc.perform(post(BASE).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"sprint\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void readsRenamesAndDeletesAGroup() throws Exception {
        when(groupService.get(GROUP_ID)).thenReturn(sprint(2));
        when(groupService.rename(GROUP_ID, new RenameGroupRequest("Vitesse"))).thenReturn(
                new GroupResponse(GROUP_ID, "Vitesse", 2, NOW));

        mockMvc.perform(get(BASE + "/" + GROUP_ID).with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sprint"));
        mockMvc.perform(patch(BASE + "/" + GROUP_ID).with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Vitesse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Vitesse"));
        mockMvc.perform(delete(BASE + "/" + GROUP_ID).with(as(Role.COACH)))
                .andExpect(status().isNoContent());
        verify(groupService).delete(GROUP_ID);
    }

    @Test
    void theGroupOfAnotherCoachAnswers404() throws Exception {
        when(groupService.get(GROUP_ID)).thenThrow(GroupErrors.groupNotFound());

        mockMvc.perform(get(BASE + "/" + GROUP_ID).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- composition

    @Test
    void listsAddsAndRemovesMembers() throws Exception {
        when(memberService.list(GROUP_ID, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(
                new GroupMemberResponse(ATHLETE_ID, "Lucas", "Girard", "lucas.girard@athlete.fr", NOW))));
        when(memberService.add(GROUP_ID, List.of(ATHLETE_ID))).thenReturn(sprint(1));

        mockMvc.perform(get(BASE + "/" + GROUP_ID + "/athletes").with(as(Role.COACH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].athleteId").value(ATHLETE_ID.toString()))
                .andExpect(jsonPath("$.content[0].addedAt").value("2026-10-23T08:15:30Z"));
        mockMvc.perform(post(BASE + "/" + GROUP_ID + "/athletes").with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"athleteIds\":[\"" + ATHLETE_ID + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.athleteCount").value(1));
        mockMvc.perform(delete(BASE + "/" + GROUP_ID + "/athletes/" + ATHLETE_ID).with(as(Role.COACH)))
                .andExpect(status().isNoContent());
        verify(memberService).remove(GROUP_ID, ATHLETE_ID);
    }

    @Test
    void refusesAnInvalidBatch() throws Exception {
        String url = BASE + "/" + GROUP_ID + "/athletes";
        mockMvc.perform(post(url).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content("{\"athleteIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post(url).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON).content("{\"athleteIds\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post(url).with(as(Role.COACH)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"athleteIds\":[\"pas-un-uuid\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(memberService);
    }

    @Test
    void memberErrorsAre404() throws Exception {
        when(memberService.add(eq(GROUP_ID), any())).thenThrow(AthleteErrors.athleteNotFound());
        doThrow(GroupErrors.memberNotFound()).when(memberService).remove(GROUP_ID, ATHLETE_ID);

        mockMvc.perform(post(BASE + "/" + GROUP_ID + "/athletes").with(as(Role.COACH))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"athleteIds\":[\"" + ATHLETE_ID + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));
        mockMvc.perform(delete(BASE + "/" + GROUP_ID + "/athletes/" + ATHLETE_ID).with(as(Role.COACH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));
    }
}
