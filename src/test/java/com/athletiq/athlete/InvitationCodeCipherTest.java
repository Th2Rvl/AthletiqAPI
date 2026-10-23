package com.athletiq.athlete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationCodeCipherTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_B = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final InvitationCodeCipher cipher = new InvitationCodeCipher(new InvitationProperties(KEY_A, KEY_B, null));

    @Test
    void encryptsAndDecryptsACode() {
        UUID coachId = UUID.randomUUID();

        byte[] blob = cipher.encrypt("K7QH3MPA", coachId);

        assertThat(blob).hasSize(37);
        assertThat(blob[0]).isEqualTo(InvitationCodeCipher.KEY_VERSION);
        assertThat(new String(blob)).doesNotContain("K7QH3MPA");
        assertThat(cipher.decrypt(blob, coachId)).isEqualTo("K7QH3MPA");
    }

    @Test
    void usesARandomIvSoTheSameCodeNeverGivesTheSameBlob() {
        UUID coachId = UUID.randomUUID();

        assertThat(cipher.encrypt("K7QH3MPA", coachId)).isNotEqualTo(cipher.encrypt("K7QH3MPA", coachId));
    }

    @Test
    void refusesATamperedBlob() {
        UUID coachId = UUID.randomUUID();
        byte[] blob = cipher.encrypt("K7QH3MPA", coachId);
        blob[blob.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(blob, coachId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("K7QH3MPA");
    }

    @Test
    void refusesABlobCopiedOntoAnotherCoach() {
        byte[] blob = cipher.encrypt("K7QH3MPA", UUID.randomUUID());

        assertThatThrownBy(() -> cipher.decrypt(blob, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAnUnknownKeyVersion() {
        UUID coachId = UUID.randomUUID();
        byte[] blob = cipher.encrypt("K7QH3MPA", coachId);
        blob[0] = 9;

        assertThatThrownBy(() -> cipher.decrypt(blob, coachId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hashIsAStableKeyedHmac() {
        String hash = cipher.hash("K7QH3MPA");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(cipher.hash("K7QH3MPA"));
        assertThat(cipher.hash("K7QH3MPB")).isNotEqualTo(hash);
        InvitationCodeCipher otherKey = new InvitationCodeCipher(new InvitationProperties(KEY_A, KEY_A, null));
        assertThat(otherKey.hash("K7QH3MPA")).isNotEqualTo(hash);
    }

    @Test
    void propertiesRefuseAMissingOrShortKeySoTheApplicationDoesNotStart() {
        assertThatThrownBy(() -> new InvitationProperties(null, KEY_B, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATHLETIQ_INVITATION_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new InvitationProperties(KEY_A, "", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATHLETIQ_INVITATION_HMAC_KEY");
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new InvitationProperties(shortKey, KEY_B, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InvitationProperties("pas du base64 !", KEY_B, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void propertiesDefaultToSevenDaysAndHideTheKeys() {
        InvitationProperties properties = new InvitationProperties(KEY_A, KEY_B, null);

        assertThat(properties.ttl()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.toString()).doesNotContain(KEY_A).doesNotContain(KEY_B);
    }
}
