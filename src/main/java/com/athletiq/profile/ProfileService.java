package com.athletiq.profile;

import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.profile.dto.CoachProfileResponse;
import com.athletiq.profile.dto.UpdateProfileRequest;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profil du coach connecté (US09).
 *
 * <p>Le service ne reçoit jamais l'identifiant du profil à modifier : il prend celui de
 * l'utilisateur courant, lu dans le JWT. Il n'y a donc pas d'autre compte que le sien que le
 * coach puisse modifier, et le rôle n'est pas modifiable (RM3).
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    static final String PROFILE_NOT_FOUND = "PROFILE_NOT_FOUND";

    private final UserService userService;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    @Transactional(readOnly = true)
    public CoachProfileResponse get() {
        return toResponse(findCurrentUser());
    }

    @Transactional
    public CoachProfileResponse update(UpdateProfileRequest request) {
        UUID userId = currentUserId();
        accessGuard.assertSelf(userId);
        User updated = userService.updateProfile(userId, request.email(), request.firstName(), request.lastName());
        return toResponse(updated);
    }

    /**
     * Identifiant du compte courant, lu dans le JWT. Le compte est cherché en base : un jeton
     * valide pour un compte supprimé ne donne accès à rien.
     */
    private UUID currentUserId() {
        UUID userId = currentUser.id();
        if (userService.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException(PROFILE_NOT_FOUND, "Ce profil n'existe pas");
        }
        return userId;
    }

    private User findCurrentUser() {
        return userService.findById(currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException(PROFILE_NOT_FOUND, "Ce profil n'existe pas"));
    }

    private CoachProfileResponse toResponse(User user) {
        return new CoachProfileResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.getCreatedAt());
    }
}
