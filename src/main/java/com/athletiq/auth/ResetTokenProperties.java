package com.athletiq.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages de la réinitialisation du mot de passe.
 *
 * <p>{@code resetTokenExposed} n'est à {@code true} qu'hors production : il permet de renvoyer
 * le jeton brut dans la réponse de la demande, faute de service d'e-mail dans le code. En
 * production, le jeton n'est jamais exposé par l'API.
 */
@ConfigurationProperties("athletiq.security.reset-token")
public record ResetTokenProperties(boolean resetTokenExposed, Duration ttl) {

    public ResetTokenProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
    }

    public boolean exposesToken() {
        return resetTokenExposed;
    }
}
