package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.ActiveSprintResponse;
import com.senior.spm.controller.response.SprintDeliverableMappingResponse;
import com.senior.spm.service.DeliverableService;
import com.senior.spm.service.ScrumGradingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final ScrumGradingService scrumGradingService;
    private final DeliverableService deliverableService;

    @GetMapping("/active")
    public ResponseEntity<ActiveSprintResponse> getActiveSprint() {
        return ResponseEntity.ok(scrumGradingService.getActiveSprint());
    }

    @GetMapping("/{id}/deliverable-mapping")
    public ResponseEntity<List<SprintDeliverableMappingResponse>> getDeliverableMappings(@PathVariable UUID id) {
        return ResponseEntity.ok(deliverableService.getMappingsBySprint(id));
    }
}
