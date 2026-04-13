package com.senior.spm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.SanitizationReport;
import com.senior.spm.controller.request.SanitizationTriggerRequest;
import com.senior.spm.service.SanitizationService;

import lombok.RequiredArgsConstructor;

/**
 * REST controller for the manual sanitization trigger.
 *
 * <p>Maps to {@code POST /api/coordinator/sanitize} which is guarded by
 * {@code hasRole("COORDINATOR")} via {@code SecurityConfig} (all {@code /api/coordinator/**}
 * routes require the Coordinator role).
 *
 * <p>The sanitization job also runs automatically via {@link SanitizationService#runSanitizationIfWindowClosed()}
 * (every 5 minutes). This endpoint gives coordinators an on-demand trigger — useful for
 * early sanitization ({@code force: true}) or immediate execution after the window closes.
 */
@RestController
@RequestMapping("/api/coordinator")
@RequiredArgsConstructor
public class SanitizationController {

    private final SanitizationService sanitizationService;

    /**
     * Manually triggers the sanitization job for the active term.
     *
     * <p>Normal trigger ({@code force = false} or body omitted): runs only if the
     * {@code ADVISOR_ASSOCIATION} window is already closed. Returns 400 if the window is
     * still active.
     *
     * <p>Force trigger ({@code force = true}): runs immediately regardless of window state.
     * Useful when the coordinator wants to clean up early.
     *
     * <p>The job disbands all groups without an assigned advisor (status {@code FORMING},
     * {@code TOOLS_PENDING}, or {@code TOOLS_BOUND}), hard-deletes their memberships, and
     * bulk AUTO_REJECTs any PENDING advisor requests — all in per-group atomic transactions.
     *
     * <p>Auth: Staff JWT with role=Coordinator (enforced by SecurityConfig).
     *
     * @param body optional request body; defaults to {@code force = false} if omitted or empty
     * @return 200 with {@link SanitizationReport} containing disbandedCount,
     *         autoRejectedRequestCount, and triggeredAt
     *
     *         Error responses:
     *         - 400: window is still active and {@code force} was not set to {@code true}
     */
    @PostMapping("/sanitize")
    public ResponseEntity<SanitizationReport> triggerSanitization(
            @RequestBody(required = false) SanitizationTriggerRequest body) {
        boolean force = (body != null) && body.isForce();
        SanitizationReport report = sanitizationService.triggerManually(force);
        return ResponseEntity.ok(report);
    }
}
