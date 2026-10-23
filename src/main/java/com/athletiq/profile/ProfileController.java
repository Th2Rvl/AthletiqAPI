package com.athletiq.profile;

import com.athletiq.profile.dto.CoachProfileResponse;
import com.athletiq.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil du coach connecté (US09). Le chemin ne porte pas d'identifiant : le compte visé est
 * celui du JWT, ce qui exclut toute modification du profil d'autrui (RM4).
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public CoachProfileResponse get() {
        return profileService.get();
    }

    @PatchMapping
    public CoachProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(request);
    }
}
