package com.athletiq.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Compte admin créé au démarrage ({@code ATHLETIQ_ADMIN_EMAIL} / {@code ATHLETIQ_ADMIN_PASSWORD}). */
@ConfigurationProperties("athletiq.admin")
public record AdminProperties(String email, String password) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminProperties[email=" + email + ", password=***]";
    }
}
