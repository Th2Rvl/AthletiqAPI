package com.athletiq.workout;

import com.athletiq.workout.dto.WorkoutRequest;
import com.athletiq.workout.dto.WorkoutResponse;
import com.athletiq.workout.dto.WorkoutSummaryResponse;
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

/** Séances du coach connecté (US18-US20). */
@RestController
@RequestMapping(WorkoutController.BASE_PATH)
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class WorkoutController {

    static final String BASE_PATH = "/api/v1/workouts";

    private final WorkoutService workoutService;

    /** Tri fixe (modification décroissante) : un paramètre {@code sort} est ignoré. */
    @GetMapping
    public PagedModel<WorkoutSummaryResponse> list(@RequestParam(required = false) @Size(max = 100) String q,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(workoutService.list(q, PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<WorkoutResponse> create(@Valid @RequestBody WorkoutRequest request) {
        return created(workoutService.create(request));
    }

    @GetMapping("/{workoutId}")
    public WorkoutResponse get(@PathVariable UUID workoutId) {
        return workoutService.get(workoutId);
    }

    @PutMapping("/{workoutId}")
    public WorkoutResponse update(@PathVariable UUID workoutId, @Valid @RequestBody WorkoutRequest request) {
        return workoutService.update(workoutId, request);
    }

    @PostMapping("/{workoutId}/duplicate")
    public ResponseEntity<WorkoutResponse> duplicate(@PathVariable UUID workoutId) {
        return created(workoutService.duplicate(workoutId));
    }

    @DeleteMapping("/{workoutId}")
    public ResponseEntity<Void> delete(@PathVariable UUID workoutId) {
        workoutService.delete(workoutId);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<WorkoutResponse> created(WorkoutResponse workout) {
        URI location = UriComponentsBuilder.fromPath(BASE_PATH + "/{id}").buildAndExpand(workout.id()).toUri();
        return ResponseEntity.created(location).body(workout);
    }
}
