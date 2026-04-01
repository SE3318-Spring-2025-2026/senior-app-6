package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.SprintRequest;
import com.senior.spm.controller.request.UpdateSprintTargetRequest;
import com.senior.spm.entity.Sprint;
import com.senior.spm.repository.SprintRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final SprintRepository sprintRepository;

    public CoordinatorController(SprintRepository sprintRepository) {
        this.sprintRepository = sprintRepository;
    }

    @PostMapping("/sprints")
    public ResponseEntity<String> createSprints(@Valid @RequestBody SprintRequest request) {
        // Validate that endDate is not before startDate
        if (request.getEndDate().isBefore(request.getStartDate())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("End date cannot be before start date");
        }

        // Create and save the sprint
        Sprint sprint = new Sprint();
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());

        Sprint savedSprint = sprintRepository.save(sprint);

        return ResponseEntity.status(HttpStatus.CREATED).body("Sprint created with ID: " + savedSprint.getId());
    }

    @PatchMapping("/sprints/{id}/target")
    public ResponseEntity<String> updateSprintTarget(@PathVariable UUID id, @Valid @RequestBody UpdateSprintTargetRequest request) {
        // Check if sprint exists
        if (!sprintRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sprint not found with ID: " + id);
        }

        // Retrieve the sprint and update the target
        Sprint sprint = sprintRepository.findById(id).get();
        sprint.setStoryPointTarget(request.getTargetStoryPoint());
        sprintRepository.save(sprint);

        return ResponseEntity.status(HttpStatus.OK).body("Sprint target updated successfully");
    }
}