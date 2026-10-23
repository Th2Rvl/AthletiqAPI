package com.athletiq.planning;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Séance planifiée (US21-US26) : un instantané placé à une date (jour sans heure, Q12) pour un athlète,
 * par le coach qui l'a créée (RM1). {@code sourceWorkoutId} et {@code groupId} sont des colonnes simples
 * pour l'affichage, mises à {@code NULL} par la base à la suppression du modèle ou du groupe.
 */
@Entity
@Table(name = "planned_session")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannedSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private SessionSnapshot snapshot;

    @Column(name = "source_workout_id")
    private UUID sourceWorkoutId;

    @Column(name = "group_id")
    private UUID groupId;
}
