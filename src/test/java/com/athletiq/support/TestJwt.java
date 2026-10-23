package com.athletiq.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.athletiq.user.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Utilisateur authentifié par JWT pour MockMvc, avec le rôle voulu. */
public final class TestJwt {

    private TestJwt() {
    }

    public static JwtRequestPostProcessor as(Role role) {
        return jwt()
                .jwt(token -> token.subject(UUID.randomUUID().toString()).claim("roles", List.of(role.name())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
