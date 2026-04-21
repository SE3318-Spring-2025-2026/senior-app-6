package com.senior.spm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.ActiveSprintResponse;
import com.senior.spm.service.ScrumGradingService;

import lombok.RequiredArgsConstructor;

/**
 * Student-facing sprint endpoints under /api/sprints.
 * New prefix — no existing controller for this path.
 * Auth: Student JWT (enforced by SecurityConfig: /api/sprints/** → STUDENT).
 */
@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class StudentSprintController {

    private final ScrumGradingService scrumGradingService;

    // =========================================================================
    // GET /api/sprints/active
    // DFD 5.6 | Issue #P5-06
    // =========================================================================
    @GetMapping("/active")
    public ResponseEntity<ActiveSprintResponse> getActiveSprint() {
        return ResponseEntity.ok(scrumGradingService.getActiveSprint());
    }
}
