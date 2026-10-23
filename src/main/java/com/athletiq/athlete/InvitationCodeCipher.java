package com.athletiq.athlete;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Protection des codes d'invitation en base (D2). Le code n'est jamais stocké en clair :
 * <ul>
 *   <li>{@link #encrypt} : AES-256-GCM, pour que le coach puisse réafficher ses codes en attente.
 *       Format : 1 octet de version de clé, IV aléatoire de 12 octets, texte chiffré et tag de
 *       128 bits (37 octets pour un code de 8 caractères). L'identifiant du coach sert de donnée
 *       authentifiée (AAD) : un code chiffré recopié sur l'invitation d'un autre coach ne se
 *       déchiffre pas.</li>
 *   <li>{@link #hash} : HMAC-SHA256 du code normalisé, en hexadécimal. C'est l'index de recherche à
 *       la saisie : on ne déchiffre jamais pour chercher. La clé secrète empêche de retrouver un code
 *       de 40 bits par force brute à partir d'une copie de la base.</li>
 * </ul>
 * Le code en clair n'est jamais journalisé.
 */
@Component
public class InvitationCodeCipher {

    static final byte KEY_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";

    private final SecretKey encryptionKey;
    private final SecretKey hmacKey;
    private final SecureRandom random = new SecureRandom();

    public InvitationCodeCipher(InvitationProperties properties) {
        this.encryptionKey = new SecretKeySpec(properties.encryptionKeyBytes(), "AES");
        this.hmacKey = new SecretKeySpec(properties.hmacKeyBytes(), HMAC);
    }

    public byte[] encrypt(String code, UUID coachId) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(coachId));
            byte[] encrypted = cipher.doFinal(code.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_BYTES + encrypted.length)
                    .put(KEY_VERSION)
                    .put(iv)
                    .put(encrypted)
                    .array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Chiffrement du code d'invitation impossible", ex);
        }
    }

    /** Refuse un blob altéré, d'une version de clé inconnue ou rattaché à un autre coach. */
    public String decrypt(byte[] blob, UUID coachId) {
        if (blob == null || blob.length <= 1 + IV_BYTES || blob[0] != KEY_VERSION) {
            throw new IllegalStateException("Code d'invitation chiffré illisible");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, blob, 1, IV_BYTES));
            cipher.updateAAD(aad(coachId));
            byte[] code = cipher.doFinal(blob, 1 + IV_BYTES, blob.length - 1 - IV_BYTES);
            return new String(code, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            // Le message ne reprend ni le code ni le blob.
            throw new IllegalStateException("Code d'invitation chiffré illisible");
        }
    }

    /** Empreinte HMAC-SHA256 d'un code déjà normalisé, en hexadécimal minuscule (64 caractères). */
    public String hash(String normalizedCode) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(normalizedCode.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Empreinte du code d'invitation impossible", ex);
        }
    }

    private static byte[] aad(UUID coachId) {
        return ByteBuffer.allocate(16)
                .putLong(coachId.getMostSignificantBits())
                .putLong(coachId.getLeastSignificantBits())
                .array();
    }
}
