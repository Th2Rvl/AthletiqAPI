package com.athletiq.planning;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.user.User;
import com.athletiq.workout.dto.PlannedSetResponse;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import com.athletiq.workout.dto.WorkoutResponse;
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
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Instantané immuable d'une séance (RM5, Q2) : nom, description, exercices (identifiant, nom et groupe
 * musculaire copiés), notes, séries et ordre. Partagé par toutes les séances planifiées d'une même
 * planification de groupe et par leurs duplications ; supprimé quand plus aucune ne le référence.
 */
@Entity
@Table(name = "session_snapshot")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SessionSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Builder.Default
    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<SessionSnapshotExercise> exercises = new ArrayList<>();

    /**
     * Copie le contenu d'une séance lue par {@code WorkoutService.get} (déjà gardée par l'AccessGuard) :
     * aucune référence vers le modèle n'est conservée dans l'instantané.
     */
    public static SessionSnapshot copyOf(User coach, WorkoutResponse workout) {
        SessionSnapshot snapshot = SessionSnapshot.builder()
                .coach(coach)
                .name(workout.name())
                .description(workout.description())
                .build();
        workout.exercises().stream()
                .sorted(Comparator.comparingInt(WorkoutExerciseResponse::position))
                .forEach(source -> {
                    SessionSnapshotExercise exercise = SessionSnapshotExercise.builder()
                            .exerciseId(source.exercise().id())
                            .exerciseName(source.exercise().name())
                            .muscleGroup(source.exercise().muscleGroup())
                            .note(source.note())
                            .build();
                    source.sets().stream()
                            .sorted(Comparator.comparingInt(PlannedSetResponse::position))
                            .forEach(set -> exercise.addSet(SessionSnapshotSet.builder()
                                    .reps(set.reps())
                                    .loadKg(set.loadKg())
                                    .restSeconds(set.restSeconds())
                                    .build()));
                    snapshot.addExercise(exercise);
                });
        return snapshot;
    }

    /** Ajoute un exercice en dernière position et pose la référence inverse. */
    public void addExercise(SessionSnapshotExercise exercise) {
        exercise.setSnapshot(this);
        exercise.setPosition(exercises.size());
        exercises.add(exercise);
    }
}
