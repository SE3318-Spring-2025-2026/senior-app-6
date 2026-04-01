package com.senior.spm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.CreateDeliverableRequest;
import com.senior.spm.controller.request.SprintRequest;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.Sprint;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.SprintRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final SprintRepository sprintRepository;
    private final DeliverableRepository deliverableRepository;

    public CoordinatorController(SprintRepository sprintRepository, DeliverableRepository deliverableRepository) {
        this.sprintRepository = sprintRepository;
        this.deliverableRepository = deliverableRepository;
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

    @PostMapping("/deliverables")
    public ResponseEntity<Deliverable> createDeliverable(@Valid @RequestBody CreateDeliverableRequest request) {
        // Validate that reviewDeadline is not before submissionDeadline
        if (request.getReviewDeadline().isBefore(request.getSubmissionDeadline())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Create and save the deliverable
        Deliverable deliverable = new Deliverable();
        deliverable.setName(request.getName());
        deliverable.setType(request.getType());
        deliverable.setSubmissionDeadline(request.getSubmissionDeadline());
        deliverable.setReviewDeadline(request.getReviewDeadline());

        Deliverable savedDeliverable = deliverableRepository.save(deliverable);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedDeliverable);
    }
}