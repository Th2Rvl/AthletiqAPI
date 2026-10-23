package com.athletiq.planning;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.exercise.MuscleGroup;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Exercice copié dans un instantané. {@code exerciseId} est une colonne simple (pas de relation JPA vers
 * {@code Exercise}) : la clé étrangère {@code RESTRICT} protège l'exercice (Q11), et le nom et le groupe
 * musculaire copiés suffisent à l'affichage.
 */
@Entity
@Table(name = "session_snapshot_exercise")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SessionSnapshotExercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private SessionSnapshot snapshot;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "exercise_name", nullable = false, length = 200)
    private String exerciseName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "muscle_group", nullable = false, length = 50)
    private MuscleGroup muscleGroup;

    /** Index dans l'instantané, 0..29. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "note", length = 500)
    private String note;

    @Builder.Default
    @OneToMany(mappedBy = "snapshotExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<SessionSnapshotSet> sets = new ArrayList<>();

    /** Ajoute une série en dernière position et pose la référence inverse. */
    public void addSet(SessionSnapshotSet set) {
        set.setSnapshotExercise(this);
        set.setPosition(sets.size());
        sets.add(set);
    }
}
