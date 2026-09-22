package com.indira.opsconsole.controller;

import com.indira.opsconsole.detection.MetricsService;
import com.indira.opsconsole.domain.entity.AppUser;
import com.indira.opsconsole.security.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/metrics — queue health, source freshness, open case counts.
 * Scoped to the calling user's accessible clients.
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;
    private final AuthHelper     authHelper;

    @GetMapping
    public ResponseEntity<MetricsService.MetricsSnapshot> getMetrics() {
        AppUser actor = authHelper.currentUser();
        List<String> clientIds = switch (actor.getRole()) {
            case OPS_LEAD, AUDITOR -> List.of("CLI-001", "CLI-002", "CLI-003", "CLI-004");
            default -> new ArrayList<>(actor.getAccessibleClientIds());
        };
        return ResponseEntity.ok(metricsService.snapshot(clientIds));
    }
}
