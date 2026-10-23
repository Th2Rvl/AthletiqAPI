package com.athletiq.workout;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.user.User;
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
 * Séance d'un coach (US18-US20) : un modèle de travail sans date, propre au coach (RM1). Ses exercices
 * sont ordonnés par {@code position} (0..n-1), et l'agrégat entier est remplacé à chaque PUT.
 */
@Entity
@Table(name = "workout")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Workout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    /** Toujours sans espaces de bord : la collation est NO PAD. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Interne, jamais exposé : incrémenté à chaque PUT pour que la ligne {@code workout} soit modifiée
     * (et {@code updatedAt} avec elle) même quand seules les séries changent.
     */
    @Column(name = "revision", nullable = false)
    private int revision;

    @Builder.Default
    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<WorkoutExercise> exercises = new ArrayList<>();

    /** Ajoute un exercice en dernière position et pose la référence inverse. */
    public void addExercise(WorkoutExercise exercise) {
        exercise.setWorkout(this);
        exercise.setPosition(exercises.size());
        exercises.add(exercise);
    }
}
