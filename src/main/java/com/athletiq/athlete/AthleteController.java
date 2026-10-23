package com.athletiq.athlete;

import com.athletiq.athlete.dto.AthleteResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Athlètes suivis par le coach connecté (US11, US12). */
@RestController
@RequestMapping("/api/v1/athletes")
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class AthleteController {

    private final AthleteService athleteService;

    @GetMapping
    public PagedModel<AthleteResponse> list(@RequestParam(required = false) @Size(max = 100) String q,
                                            @RequestParam(required = false) UUID groupId,
                                            @RequestParam(required = false) UUID excludeGroupId,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(athleteService.list(q, groupId, excludeGroupId, PageRequest.of(page, size)));
    }

    @DeleteMapping("/{athleteId}")
    public ResponseEntity<Void> remove(@PathVariable UUID athleteId) {
        athleteService.remove(athleteId);
        return ResponseEntity.noContent().build();
    }
}
