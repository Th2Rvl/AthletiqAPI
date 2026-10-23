package com.athletiq.athlete;

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
 * Invitation d'un athlète par un coach (D1) : un code à usage unique, valable 7 jours. Le code
 * n'est conservé que chiffré ({@code codeEncrypted}) et sous forme d'empreinte HMAC
 * ({@code codeHash}), jamais en clair (D2).
 */
@Entity
@Table(name = "coach_invitation")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CoachInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    /** Libellé libre qui aide le coach à reconnaître l'athlète attendu. */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "code_encrypted", nullable = false, length = 64)
    private byte[] codeEncrypted;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumed_by_id")
    private User consumedBy;

    /** Ni révoquée, ni consommée, ni expirée à cet instant. */
    public boolean isPendingAt(Instant now) {
        return revokedAt == null && consumedAt == null && expiresAt.isAfter(now);
    }
}
