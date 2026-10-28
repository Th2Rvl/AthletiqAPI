package com.athletiq.performance;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.planning.SessionSnapshotSet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Série réalisée, en face d'une série prévue de l'instantané (Q2) : au plus une par série prévue et par
 * réalisation. {@code reps} vide = série non faite ; une série prévue sans ligne est aussi non faite.
 */
@Entity
@Table(name = "performed_set")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PerformedSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private SessionPerformance performance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_set_id", nullable = false)
    private SessionSnapshotSet snapshotSet;

    /** Répétitions réalisées, 1..100 ; {@code null} = série non faite. */
    @Column(name = "reps")
    private Integer reps;

    /** En kg, au pas de 0,5 ; {@code null} = poids du corps (charge vide, exclue des volumes). */
    @Column(name = "load_kg", precision = 5, scale = 1)
    private BigDecimal loadKg;
}
