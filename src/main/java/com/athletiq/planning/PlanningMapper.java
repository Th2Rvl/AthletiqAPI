package com.athletiq.planning;

import com.athletiq.planning.dto.PlannedSessionDetailResponse;
import com.athletiq.user.UserName;
import com.athletiq.workout.dto.ExerciseRef;
import com.athletiq.workout.dto.PlannedSetResponse;
import com.athletiq.workout.dto.WorkoutExerciseResponse;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlanningMapper {

    PlannedSetResponse toSetResponse(SessionSnapshotSet set);

    /** Même forme qu'un exercice de séance : l'exercice de bibliothèque est rebâti à partir de la copie. */
    default WorkoutExerciseResponse toExerciseResponse(SessionSnapshotExercise exercise) {
        List<PlannedSetResponse> sets = exercise.getSets().stream()
                .sorted(Comparator.comparingInt(SessionSnapshotSet::getPosition))
                .map(this::toSetResponse)
                .toList();
        return new WorkoutExerciseResponse(exercise.getPosition(),
                new ExerciseRef(exercise.getExerciseId(), exercise.getExerciseName(), exercise.getMuscleGroup()),
                exercise.getNote(), sets);
    }

    /** Toujours triés par position, quel que soit l'ordre de chargement. */
    default List<WorkoutExerciseResponse> toExerciseResponses(List<SessionSnapshotExercise> exercises) {
        return exercises.stream()
                .sorted(Comparator.comparingInt(SessionSnapshotExercise::getPosition))
                .map(this::toExerciseResponse)
                .toList();
    }

    /** Le nom de l'athlète, le verrouillage et le statut sont calculés par le service et passés en paramètres. */
    default PlannedSessionDetailResponse toDetail(PlannedSession plannedSession, SessionSnapshot snapshot,
                                                  UserName athlete, boolean locked, SessionStatus status) {
        return new PlannedSessionDetailResponse(
                plannedSession.getId(),
                plannedSession.getAthlete().getId(),
                athlete == null ? null : athlete.firstName(),
                athlete == null ? null : athlete.lastName(),
                plannedSession.getScheduledDate(),
                plannedSession.getGroupId(),
                plannedSession.getSourceWorkoutId(),
                locked,
                status,
                snapshot.getName(),
                snapshot.getDescription(),
                toExerciseResponses(snapshot.getExercises()),
                plannedSession.getCreatedAt(),
                plannedSession.getUpdatedAt());
    }
}
