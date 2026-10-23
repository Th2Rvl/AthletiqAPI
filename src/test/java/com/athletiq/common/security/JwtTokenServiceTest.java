package com.athletiq.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.athletiq.user.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-key-only-for-tests-0123456789abcdef";

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(15), "athletiq-api");
    private final SecretKey key = config.jwtSecretKey(properties);
    private final JwtDecoder decoder = config.jwtDecoder(key, properties);

    @Test
    void issuesAnHs256TokenWithSubjectRolesAndExpiry() {
        UUID userId = UUID.randomUUID();
        JwtTokenService service = new JwtTokenService(config.jwtEncoder(key), properties, Clock.systemUTC());

        Jwt jwt = decoder.decode(service.createAccessToken(userId, Role.ADMIN));

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("ADMIN"));
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("athletiq-api");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getClaims()).doesNotContainKey("email");
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsAnExpiredToken() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        JwtTokenService service = new JwtTokenService(config.jwtEncoder(key), properties, past);
        String token = service.createAccessToken(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        JwtProperties other = new JwtProperties(SECRET, Duration.ofMinutes(15), "autre-emetteur");
        String token = new JwtTokenService(config.jwtEncoder(key), other, Clock.systemUTC())
                .createAccessToken(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void refusesASecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtProperties("trop-court", Duration.ofMinutes(15), "athletiq-api"))
                .isInstanceOf(IllegalStateException.class);
    }
}
