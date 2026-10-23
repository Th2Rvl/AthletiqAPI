package com.athletiq.auth;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Jeton de réinitialisation du mot de passe, à usage unique et expirant.
 *
 * <p>Seule l'empreinte SHA-256 du jeton est stockée : le jeton brut n'existe qu'entre la
 * génération et sa transmission au demandeur. {@code usedAt} rend le jeton inutilisable dès
 * qu'il a servi.
 */
@Entity
@Table(name = "password_reset_token")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Empreinte SHA-256 du jeton, en hexadécimal minuscule. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Renseigné à la première utilisation du jeton. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Vrai si le jeton peut encore servir : non utilisé et non expiré. */
    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
