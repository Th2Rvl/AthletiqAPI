package com.athletiq.performance;

import com.athletiq.performance.dto.AthletePerformanceResponse;
import com.athletiq.performance.dto.SavePerformanceRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Saisie du réalisé par l'athlète connecté (#10, utilisée par l'app mobile). */
@RestController
@RequestMapping("/api/v1/me/planned-sessions/{plannedSessionId}/performance")
@PreAuthorize("hasRole('ATHLETE')")
@RequiredArgsConstructor
public class AthletePerformanceController {

    private final AthletePerformanceService service;

    @GetMapping
    public AthletePerformanceResponse get(@PathVariable UUID plannedSessionId) {
        return service.get(plannedSessionId);
    }

    /** Brouillon, ou correction d'une séance validée : remplace le réalisé enregistré. */
    @PutMapping
    public AthletePerformanceResponse save(@PathVariable UUID plannedSessionId,
                                           @Valid @RequestBody SavePerformanceRequest request) {
        return service.save(plannedSessionId, request);
    }

    @PostMapping("/validate")
    public AthletePerformanceResponse validate(@PathVariable UUID plannedSessionId,
                                               @Valid @RequestBody SavePerformanceRequest request) {
        return service.validate(plannedSessionId, request);
    }
}
