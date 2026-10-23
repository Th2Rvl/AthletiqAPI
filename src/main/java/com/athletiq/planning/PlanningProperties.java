package com.athletiq.planning;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages de la planification ({@code athletiq.planning}). {@code zone} est le fuseau unique dans lequel
 * « aujourd'hui » est calculé (Q12), identique pour le web et le mobile ; {@code Europe/Paris} par défaut.
 */
@ConfigurationProperties("athletiq.planning")
public record PlanningProperties(ZoneId zone) {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");

    public PlanningProperties {
        if (zone == null) {
            zone = DEFAULT_ZONE;
        }
    }
}
