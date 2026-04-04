package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.CreateSprintDeliverableMappingRequest;
import com.senior.spm.service.SprintService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coordinator/sprints")
@Validated
public class CoordinatorSprintController {

    private final SprintService sprintService;

    public CoordinatorSprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @PostMapping("/{id}/deliverable-mapping")
    public ResponseEntity<Void> createDeliverableMapping(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSprintDeliverableMappingRequest request) {

        sprintService.createDeliverableMapping(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}