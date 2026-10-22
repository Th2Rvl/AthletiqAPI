package com.athletiq.common.security;

import com.athletiq.user.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Identité de l'utilisateur courant, lue dans le {@code SecurityContext} (donc dans le JWT vérifié),
 * jamais dans un paramètre envoyé par le client.
 */
@Component
public class CurrentUser {

    private static final String ROLE_PREFIX = "ROLE_";

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new AuthenticationCredentialsNotFoundException("Aucun utilisateur authentifié");
    }

    /** Le rôle de l'utilisateur courant, vide s'il n'est pas authentifié. */
    public Optional<Role> role() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .flatMap(name -> parseRole(name).stream())
                .findFirst();
    }

    private static Optional<Role> parseRole(String name) {
        try {
            return Optional.of(Role.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
