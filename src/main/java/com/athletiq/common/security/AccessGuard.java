package com.athletiq.common.security;

import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.user.Role;
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
}
