package com.athletiq.group;

import com.athletiq.group.dto.AddGroupMembersRequest;
import com.athletiq.group.dto.CreateGroupRequest;
import com.athletiq.group.dto.GroupMemberResponse;
import com.athletiq.group.dto.GroupResponse;
import com.athletiq.group.dto.RenameGroupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Groupes du coach connecté (US13) et leur composition (US14). */
@RestController
@RequestMapping(GroupController.BASE_PATH)
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class GroupController {

    static final String BASE_PATH = "/api/v1/groups";

    private final GroupService groupService;
    private final GroupMemberService memberService;

    @GetMapping
    public PagedModel<GroupResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(groupService.list(PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse created = groupService.create(request);
        URI location = UriComponentsBuilder.fromPath(BASE_PATH + "/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@PathVariable UUID groupId) {
        return groupService.get(groupId);
    }

    @PatchMapping("/{groupId}")
    public GroupResponse rename(@PathVariable UUID groupId, @Valid @RequestBody RenameGroupRequest request) {
        return groupService.rename(groupId, request);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId) {
        groupService.delete(groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/athletes")
    public PagedModel<GroupMemberResponse> listMembers(@PathVariable UUID groupId,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(memberService.list(groupId, PageRequest.of(page, size)));
    }

    @PostMapping("/{groupId}/athletes")
    public GroupResponse addMembers(@PathVariable UUID groupId, @Valid @RequestBody AddGroupMembersRequest request) {
        return memberService.add(groupId, request.athleteIds());
    }

    @DeleteMapping("/{groupId}/athletes/{athleteId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID groupId, @PathVariable UUID athleteId) {
        memberService.remove(groupId, athleteId);
        return ResponseEntity.noContent().build();
    }
}
