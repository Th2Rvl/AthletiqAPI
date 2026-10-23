package com.athletiq.planning;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active les propriétés de la planification. */
@Configuration
@EnableConfigurationProperties(PlanningProperties.class)
public class PlanningConfiguration {
}
