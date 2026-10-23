package com.athletiq.athlete;

import com.athletiq.athlete.dto.CreateInvitationRequest;
import com.athletiq.athlete.dto.InvitationResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Invitations d'athlètes du coach connecté (US10). */
@RestController
@RequestMapping(InvitationController.BASE_PATH)
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class InvitationController {

    static final String BASE_PATH = "/api/v1/invitations";

    private final InvitationService invitationService;

    @PostMapping
    public ResponseEntity<InvitationResponse> create(@Valid @RequestBody CreateInvitationRequest request) {
        InvitationResponse created = invitationService.create(request);
        URI location = UriComponentsBuilder.fromPath(BASE_PATH + "/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** {@code size} au-delà de 100 : 400 {@code INVALID_PARAMETER} (et non une page tronquée sans le dire). */
    @GetMapping
    public PagedModel<InvitationResponse> listPending(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(invitationService.listPending(PageRequest.of(page, size)));
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID invitationId) {
        invitationService.revoke(invitationId);
        return ResponseEntity.noContent().build();
    }
}
