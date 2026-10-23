package com.athletiq.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.athletiq.common.exception.ForbiddenActionException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AccessGuardTest {

    private final CurrentUser currentUser = new CurrentUser();
    private final AccessGuard accessGuard = new AccessGuard(currentUser);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static UUID authenticateAs(String role) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(id.toString()).claim("roles", List.of(role)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        return id;
    }

    @Test
    void letsAnAdminThrough() {
        UUID id = authenticateAs("ADMIN");

        assertThatCode(accessGuard::assertAdmin).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(currentUser.id()).isEqualTo(id);
    }

    @Test
    void refusesACoach() {
        authenticateAs("COACH");

        assertThatThrownBy(accessGuard::assertAdmin)
                .isInstanceOf(ForbiddenActionException.class)
                .extracting("code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    void refusesAnAnonymousCaller() {
        assertThatThrownBy(accessGuard::assertAdmin).isInstanceOf(ForbiddenActionException.class);
    }
}
