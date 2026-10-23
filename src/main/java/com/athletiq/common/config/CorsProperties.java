package com.athletiq.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Origines autorisées ({@code athletiq.cors.allowed-origins}), propres à chaque profil. */
@ConfigurationProperties("athletiq.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
