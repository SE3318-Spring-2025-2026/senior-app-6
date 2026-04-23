package com.senior.spm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.ActiveSprintResponse;
import com.senior.spm.service.ScrumGradingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final ScrumGradingService scrumGradingService;

    @GetMapping("/active")
    public ResponseEntity<ActiveSprintResponse> getActiveSprint() {
        return ResponseEntity.ok(scrumGradingService.getActiveSprint());
    }
}
