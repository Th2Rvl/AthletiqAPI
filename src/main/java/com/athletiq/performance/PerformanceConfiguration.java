package com.athletiq.performance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active les seuils du tableau de bord. */
@Configuration
@EnableConfigurationProperties(KpiProperties.class)
public class PerformanceConfiguration {
}
