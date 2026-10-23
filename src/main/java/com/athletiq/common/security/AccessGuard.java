package com.athletiq.common.security;

import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.user.Role;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Contrôles d'accès appelés par les services, avant toute lecture ou modification.
 * C'est la dernière ligne de défense, après la {@code SecurityFilterChain} et {@code @PreAuthorize}.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final CurrentUser currentUser;

    /** L'utilisateur courant doit être administrateur de la plateforme. */
    public void assertAdmin() {
        if (currentUser.role().filter(Role.ADMIN::equals).isEmpty()) {
            throw new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé");
        }
    }

    /**
     * L'utilisateur courant doit agir sur son propre compte (RM4). Un compte ne peut modifier que
     * son profil : l'identité vient du JWT, jamais d'un paramètre envoyé par le client.
     */
    public void assertSelf(UUID userId) {
        if (!currentUser.id().equals(userId)) {
            throw new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé");
        }
    }
}
