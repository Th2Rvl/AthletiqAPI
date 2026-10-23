package com.athletiq.athlete;

import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.athlete.dto.RedeemInvitationRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Coachs de l'athlète connecté : saisie d'un code d'invitation (P5, utilisée par l'app mobile #47). */
@RestController
@RequestMapping(CoachLinkController.BASE_PATH)
@PreAuthorize("hasRole('ATHLETE')")
@RequiredArgsConstructor
public class CoachLinkController {

    static final String BASE_PATH = "/api/v1/me/coaches";

    private final InvitationService invitationService;

    @PostMapping
    public ResponseEntity<CoachLinkResponse> redeem(@Valid @RequestBody RedeemInvitationRequest request) {
        return ResponseEntity.created(URI.create(BASE_PATH)).body(invitationService.redeem(request));
    }
}
