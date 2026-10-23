package com.athletiq.exercise;

import com.athletiq.exercise.dto.CreateExerciseRequest;
import com.athletiq.exercise.dto.ExerciseResponse;
import com.athletiq.exercise.dto.UpdateExerciseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Bibliothèque d'exercices du coach connecté (US15-US17). */
@RestController
@RequestMapping(ExerciseController.BASE_PATH)
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class ExerciseController {

    static final String BASE_PATH = "/api/v1/exercises";

    private final ExerciseService exerciseService;

    @GetMapping
    public PagedModel<ExerciseResponse> list(@RequestParam(required = false) @Size(max = 100) String q,
                                             @RequestParam(required = false) MuscleGroup muscleGroup,
                                             @RequestParam(required = false) @Size(max = 100) String equipment,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(exerciseService.list(q, muscleGroup, equipment, PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<ExerciseResponse> create(@Valid @RequestBody CreateExerciseRequest request) {
        ExerciseResponse created = exerciseService.create(request);
        URI location = UriComponentsBuilder.fromPath(BASE_PATH + "/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{exerciseId}")
    public ExerciseResponse get(@PathVariable UUID exerciseId) {
        return exerciseService.get(exerciseId);
    }

    @PutMapping("/{exerciseId}")
    public ExerciseResponse update(@PathVariable UUID exerciseId,
                                   @Valid @RequestBody UpdateExerciseRequest request) {
        return exerciseService.update(exerciseId, request);
    }

    @DeleteMapping("/{exerciseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID exerciseId) {
        exerciseService.delete(exerciseId);
        return ResponseEntity.noContent().build();
    }
}
