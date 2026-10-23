package com.athletiq.workout;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.exercise.Exercise;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Exercice de la bibliothèque du coach placé dans une séance (RM2, RM3). Le même exercice peut y
 * figurer plusieurs fois, à des positions différentes (Q11).
 */
@Entity
@Table(name = "workout_exercise")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkoutExercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    /** Index dans la séance, 0..29, unique par séance. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "note", length = 500)
    private String note;

    @Builder.Default
    @OneToMany(mappedBy = "workoutExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PlannedSet> sets = new ArrayList<>();

    /** Ajoute une série en dernière position et pose la référence inverse. */
    public void addSet(PlannedSet set) {
        set.setWorkoutExercise(this);
        set.setPosition(sets.size());
        sets.add(set);
    }
}
