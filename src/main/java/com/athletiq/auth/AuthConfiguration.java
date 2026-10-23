package com.athletiq.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active les propriétés de la réinitialisation du mot de passe. */
@Configuration
@EnableConfigurationProperties(ResetTokenProperties.class)
public class AuthConfiguration {
}
