package com.athletiq.performance;

import com.athletiq.performance.dto.CoachKpisResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tableau de bord des KPI du coach connecté (US32), au coach uniquement (RM2). */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasRole('COACH')")
@RequiredArgsConstructor
public class KpiController {

    private final KpiService kpiService;

    /** {@code weeks} : 4 (par défaut) ou 12 ; {@code groupId} facultatif. */
    @GetMapping("/kpis")
    public CoachKpisResponse kpis(@RequestParam(defaultValue = "4") int weeks,
                                  @RequestParam(required = false) UUID groupId) {
        return kpiService.kpis(weeks, groupId);
    }
}
