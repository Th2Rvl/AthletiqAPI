package com.athletiq.performance;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.planning.PlannedSession;
import com.athletiq.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Réalisation d'une séance planifiée (Q2), au plus une par séance (RM4). Écrite par l'athlète dans #10 :
 * brouillon, puis validation ({@code validatedAt}), puis corrections éventuelles ({@code correctedAt}, Q3).
 * Le coach n'en lit que la version validée (RM3). {@code athlete} recopie l'athlète de la séance planifiée,
 * pour les lectures de l'athlète (#11).
 */
@Entity
@Table(name = "session_performance")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SessionPerformance extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planned_session_id", nullable = false, unique = true)
    private PlannedSession plannedSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    /** VARCHAR en base (contrainte CHECK dans la migration), sans {@code @JdbcTypeCode} Hibernate attendrait un ENUM. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private PerformanceStatus status;

    /** RPE de la séance, 1..10 ; {@code null} = non renseigné (jamais 0). TINYINT en base. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rpe")
    private Integer rpe;

    /** Fatigue, 1..5 ; {@code null} = non renseignée. TINYINT en base. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "fatigue")
    private Integer fatigue;

    @Column(name = "comment", length = 1000)
    private String comment;

    /** Première validation ; présente si et seulement si la réalisation est validée (CHECK en base). */
    @Column(name = "validated_at")
    private Instant validatedAt;

    /** Dernière correction après validation (« Modifiée le … ») ; {@code null} si jamais corrigée. */
    @Column(name = "corrected_at")
    private Instant correctedAt;

    @Builder.Default
    @OneToMany(mappedBy = "performance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PerformedSet> sets = new ArrayList<>();

    /** Ajoute une série réalisée et pose la référence inverse. */
    public void addSet(PerformedSet set) {
        set.setPerformance(this);
        sets.add(set);
    }
}
