package com.athletiq.workout;

import com.athletiq.common.persistence.BaseEntity;
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

/** Série prévue d'un exercice de séance (Q1 à Q4) : une ligne par série. */
@Entity
@Table(name = "planned_set")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannedSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_exercise_id", nullable = false)
    private WorkoutExercise workoutExercise;

    /** Index dans l'exercice de séance, 0..19, unique par exercice de séance. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "reps", nullable = false)
    private int reps;

    /** En kg, au pas de 0,5 ; {@code null} = poids du corps. */
    @Column(name = "load_kg", precision = 5, scale = 1)
    private BigDecimal loadKg;

    /** En secondes, 0..600 ; {@code null} = non précisé. */
    @Column(name = "rest_seconds")
    private Integer restSeconds;
}
