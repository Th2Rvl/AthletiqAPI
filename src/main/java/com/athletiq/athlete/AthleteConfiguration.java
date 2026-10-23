package com.athletiq.athlete;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active les propriétés des codes d'invitation. */
@Configuration
@EnableConfigurationProperties(InvitationProperties.class)
public class AthleteConfiguration {
}
