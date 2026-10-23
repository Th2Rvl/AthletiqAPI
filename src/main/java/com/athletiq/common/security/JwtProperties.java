package com.athletiq.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration JWT ({@code athletiq.security.jwt}). Le secret vient de la variable d'environnement
 * {@code ATHLETIQ_JWT_SECRET} et doit faire au moins 32 octets (256 bits, exigence de HS256) :
 * sinon l'application refuse de démarrer.
 */
@ConfigurationProperties("athletiq.security.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, String issuer) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "athletiq.security.jwt.secret (ATHLETIQ_JWT_SECRET) doit faire au moins "
                            + MIN_SECRET_BYTES + " octets");
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "athletiq-api";
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Masque le secret si l'objet est loggé. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, accessTokenTtl=" + accessTokenTtl + ", issuer=" + issuer + "]";
    }
}
