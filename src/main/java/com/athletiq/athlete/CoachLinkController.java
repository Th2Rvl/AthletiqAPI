package com.athletiq.athlete;

import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.athlete.dto.RedeemInvitationRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Coachs de l'athlète connecté : code d'invitation (P5), consultation et départ (#8, app mobile). */
@RestController
@RequestMapping(CoachLinkController.BASE_PATH)
@PreAuthorize("hasRole('ATHLETE')")
@RequiredArgsConstructor
public class CoachLinkController {

    static final String BASE_PATH = "/api/v1/me/coaches";

    private final InvitationService invitationService;
    private final MyCoachesService myCoachesService;

    @PostMapping
    public ResponseEntity<CoachLinkResponse> redeem(@Valid @RequestBody RedeemInvitationRequest request) {
        return ResponseEntity.created(URI.create(BASE_PATH)).body(invitationService.redeem(request));
    }

    @GetMapping
    public List<CoachLinkResponse> list() {
        return myCoachesService.list();
    }

    @DeleteMapping("/{coachId}")
    public ResponseEntity<Void> leave(@PathVariable UUID coachId) {
        myCoachesService.leave(coachId);
        return ResponseEntity.noContent().build();
    }
}
