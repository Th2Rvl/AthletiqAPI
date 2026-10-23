package com.athletiq.athlete;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages des codes d'invitation ({@code athletiq.invitation}).
 *
 * <p>Les deux clés (32 octets encodés en base64) viennent des variables d'environnement
 * {@code ATHLETIQ_INVITATION_ENCRYPTION_KEY} et {@code ATHLETIQ_INVITATION_HMAC_KEY}. Seuls les
 * profils {@code dev} et {@code test} ont une valeur par défaut : ailleurs, une clé absente ou mal
 * formée empêche l'application de démarrer, plutôt que de chiffrer avec une clé connue.
 */
@ConfigurationProperties("athletiq.invitation")
public record InvitationProperties(String encryptionKey, String hmacKey, Duration ttl) {

    public static final int KEY_BYTES = 32;

    public InvitationProperties {
        decodeKey(encryptionKey, "athletiq.invitation.encryption-key (ATHLETIQ_INVITATION_ENCRYPTION_KEY)");
        decodeKey(hmacKey, "athletiq.invitation.hmac-key (ATHLETIQ_INVITATION_HMAC_KEY)");
        if (ttl == null) {
            ttl = Duration.ofDays(7);
        }
    }

    public byte[] encryptionKeyBytes() {
        return decodeKey(encryptionKey, "encryption-key");
    }

    public byte[] hmacKeyBytes() {
        return decodeKey(hmacKey, "hmac-key");
    }

    private static byte[] decodeKey(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " est obligatoire (32 octets encodés en base64)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value.strip());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException(name + " doit être encodée en base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(name + " doit faire exactement " + KEY_BYTES + " octets");
        }
        return bytes;
    }

    /** Masque les clés si l'objet est loggé. */
    @Override
    public String toString() {
        return "InvitationProperties[encryptionKey=***, hmacKey=***, ttl=" + ttl + "]";
    }
}
