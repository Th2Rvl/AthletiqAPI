package com.athletiq.workout;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, UUID> {

    /**
     * Détail, seconde requête : initialise les séries des exercices de séance déjà chargés par
     * {@link WorkoutRepository#findDetailByIdAndCoachId} (même contexte de persistance).
     */
    @Query("""
            select distinct we from WorkoutExercise we
            left join fetch we.sets
            where we.workout.id = :workoutId
            """)
    List<WorkoutExercise> fetchSetsOfWorkout(@Param("workoutId") UUID workoutId);

    /** Liste : exercices de chaque séance de la page, par groupe musculaire, en une requête. */
    @Query("""
            select new com.athletiq.workout.WorkoutMuscleGroupCount(we.workout.id, e.muscleGroup, count(we))
            from WorkoutExercise we
            join we.exercise e
            where we.workout.id in :workoutIds
            group by we.workout.id, e.muscleGroup
            """)
    List<WorkoutMuscleGroupCount> countByMuscleGroup(@Param("workoutIds") Collection<UUID> workoutIds);

    /** Nombre de séances distinctes qui utilisent cet exercice (RM9). */
    @Query("select count(distinct we.workout.id) from WorkoutExercise we where we.exercise.id = :exerciseId")
    long countWorkoutsUsing(@Param("exerciseId") UUID exerciseId);
}
