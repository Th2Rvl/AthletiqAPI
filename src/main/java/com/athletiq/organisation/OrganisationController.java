package com.athletiq.organisation;

import com.athletiq.organisation.dto.CreateOrganisationRequest;
import com.athletiq.organisation.dto.InvitationResponse;
import com.athletiq.organisation.dto.InviteCoachRequest;
import com.athletiq.organisation.dto.OrganisationAthleteResponse;
import com.athletiq.organisation.dto.OrganisationCoachResponse;
import com.athletiq.organisation.dto.OrganisationResponse;
import com.athletiq.organisation.dto.UpdateOrganisationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Organisations, coachs membres et invitations. Réservé à l'administrateur de la plateforme
 * (contrôlé aussi par la SecurityFilterChain et par le service).
 */
@RestController
@RequestMapping(OrganisationController.BASE_PATH)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class OrganisationController {

    static final String BASE_PATH = "/api/v1/organisations";

    private final OrganisationService organisationService;

    @GetMapping
    public PagedModel<OrganisationResponse> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(organisationService.list(q, pageable));
    }

    @PostMapping
    public ResponseEntity<OrganisationResponse> create(@Valid @RequestBody CreateOrganisationRequest request) {
        OrganisationResponse created = organisationService.create(request);
        return ResponseEntity.created(location("/{id}", created.id())).body(created);
    }

    @GetMapping("/{organisationId}")
    public OrganisationResponse get(@PathVariable UUID organisationId) {
        return organisationService.get(organisationId);
    }

    @PutMapping("/{organisationId}")
    public OrganisationResponse update(@PathVariable UUID organisationId,
                                       @Valid @RequestBody UpdateOrganisationRequest request) {
        return organisationService.update(organisationId, request);
    }

    @DeleteMapping("/{organisationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID organisationId) {
        organisationService.delete(organisationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{organisationId}/coaches")
    public PagedModel<OrganisationCoachResponse> listCoaches(@PathVariable UUID organisationId,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(organisationService.listCoaches(organisationId, pageable));
    }

    @DeleteMapping("/{organisationId}/coaches/{coachId}")
    public ResponseEntity<Void> removeCoach(@PathVariable UUID organisationId, @PathVariable UUID coachId) {
        organisationService.removeCoach(organisationId, coachId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{organisationId}/invitations")
    public ResponseEntity<InvitationResponse> inviteCoach(@PathVariable UUID organisationId,
                                                          @Valid @RequestBody InviteCoachRequest request) {
        InvitationResponse invitation = organisationService.inviteCoach(organisationId, request.email());
        return ResponseEntity.created(location("/{organisationId}/invitations/{id}", organisationId, invitation.id()))
                .body(invitation);
    }

    @GetMapping("/{organisationId}/invitations")
    public PagedModel<InvitationResponse> listInvitations(
            @PathVariable UUID organisationId,
            @RequestParam(defaultValue = "PENDING") InvitationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(organisationService.listInvitations(organisationId, status, pageable));
    }

    @GetMapping("/{organisationId}/invitations/{invitationId}")
    public InvitationResponse getInvitation(@PathVariable UUID organisationId, @PathVariable UUID invitationId) {
        return organisationService.getInvitation(organisationId, invitationId);
    }

    @DeleteMapping("/{organisationId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancelInvitation(@PathVariable UUID organisationId,
                                                 @PathVariable UUID invitationId) {
        organisationService.cancelInvitation(organisationId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{organisationId}/athletes")
    public PagedModel<OrganisationAthleteResponse> listAthletes(@PathVariable UUID organisationId,
                                                                @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(organisationService.listAthletes(organisationId, pageable));
    }

    /** {@code Location} relatif à la racine de l'API, comme décrit dans le contrat. */
    private static URI location(String path, Object... variables) {
        return UriComponentsBuilder.fromPath(BASE_PATH + path).buildAndExpand(variables).toUri();
    }
}
