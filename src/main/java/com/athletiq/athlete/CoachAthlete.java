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
 * Lien de suivi entre un coach et un athlète (RM1, RM2). Le retrait clôture le lien
 * ({@code endedAt}) au lieu de le supprimer, pour garder l'historique (D5).
 *
 * <p>La colonne générée {@code active_key} (non mappée) porte l'index unique qui garantit un seul
 * lien actif par couple (coach, athlète).
 */
@Entity
@Table(name = "coach_athlete")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CoachAthlete extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
