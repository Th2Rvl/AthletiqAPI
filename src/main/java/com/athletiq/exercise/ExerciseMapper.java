package com.athletiq.exercise;

import com.athletiq.exercise.dto.ExerciseResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExerciseMapper {

    ExerciseResponse toResponse(Exercise exercise);
}
