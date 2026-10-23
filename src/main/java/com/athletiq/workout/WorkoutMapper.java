package com.athletiq.workout;

import com.athletiq.exercise.Exercise;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.workout.dto.ExerciseRef;
import com.athletiq.workout.dto.PlannedSetResponse;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import com.athletiq.workout.dto.WorkoutResponse;
import com.athletiq.workout.dto.WorkoutSummaryResponse;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkoutMapper {

    WorkoutResponse toResponse(Workout workout);

    WorkoutExerciseResponse toResponse(WorkoutExercise workoutExercise);

    PlannedSetResponse toResponse(PlannedSet set);

    ExerciseRef toRef(Exercise exercise);

    /** Le nombre d'exercices et les groupes musculaires sont agrégés par le service et passés en paramètres. */
    @Mapping(target = "exerciseCount", source = "exerciseCount")
    @Mapping(target = "muscleGroups", source = "muscleGroups")
    WorkoutSummaryResponse toSummary(Workout workout, long exerciseCount, List<MuscleGroup> muscleGroups);

    /** Toujours triés par position, quel que soit l'ordre de chargement. */
    default List<WorkoutExerciseResponse> toExerciseResponses(List<WorkoutExercise> exercises) {
        return exercises.stream()
                .sorted(Comparator.comparingInt(WorkoutExercise::getPosition))
                .map(this::toResponse)
                .toList();
    }

    default List<PlannedSetResponse> toSetResponses(List<PlannedSet> sets) {
        return sets.stream()
                .sorted(Comparator.comparingInt(PlannedSet::getPosition))
                .map(this::toResponse)
                .toList();
    }
}
