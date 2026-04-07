package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.RubricRequest;
import com.senior.spm.controller.request.CreateDeliverableRequest;
import com.senior.spm.controller.request.MapDeliverablesRequest;
import com.senior.spm.controller.request.SprintRequest;
import com.senior.spm.controller.request.StudentUploadRequest;
import com.senior.spm.controller.request.UpdateDeliverableRequest;
import com.senior.spm.controller.request.UpdateDeliverableWeightRequest;
import com.senior.spm.controller.request.UpdateSprintTargetRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.DeliverableService;
import com.senior.spm.service.SprintService;
import com.senior.spm.service.StudentService;
import com.senior.spm.service.SystemStateService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final SprintService sprintService;
    private final DeliverableService deliverableService;
    private final StudentService studentService;
    private final SystemStateService systemStateService;

    public CoordinatorController(SprintService sprintService,
            DeliverableService deliverableService,
            StudentService studentService,
            SystemStateService systemStateService) {
        this.sprintService = sprintService;
        this.deliverableService = deliverableService;
        this.studentService = studentService;
        this.systemStateService = systemStateService;
    }

    @PostMapping("/sprints")
    public ResponseEntity<?> createSprints(@Valid @RequestBody SprintRequest request) {
        try {
            var savedSprint = sprintService.createSprint(request.getStartDate(), request.getEndDate());
            return ResponseEntity.status(HttpStatus.CREATED).body(savedSprint);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/deliverables")
    public ResponseEntity<?> createDeliverable(@Valid @RequestBody CreateDeliverableRequest request) {
        try {
            var savedDeliverable = deliverableService.createDeliverable(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedDeliverable);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    @GetMapping("/deliverables")
    public ResponseEntity<?> getAllDeliverables() {
        var deliverables = deliverableService.getAllDeliverables();
        return ResponseEntity.status(HttpStatus.OK).body(deliverables);
    }

    @PatchMapping("/deliverables/{id}/weight")
    public ResponseEntity<?> updateDeliverableWeight(@PathVariable UUID id,
            @Valid @RequestBody UpdateDeliverableWeightRequest request) {
        try {
            var updatedDeliverable = deliverableService.updateDeliverableWeight(id, request.getWeightPercentage());
            return ResponseEntity.status(HttpStatus.OK).body(updatedDeliverable);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/deliverables/{id}/rubric")
    public ResponseEntity<?> addRubricToDeliverable(@PathVariable UUID id,
            @Valid @RequestBody AddRubricRequest request) {
        try {
            var rubricCriterion = deliverableService.addRubricToDeliverable(id, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(rubricCriterion);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/sprints/{id}/deliverable-mapping")
    public ResponseEntity<?> mapDeliverablesToSprint(@PathVariable UUID id,
            @RequestBody MapDeliverablesRequest request) {
        try {
            deliverableService.mapDeliverablesToSprint(id, request);
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PatchMapping("/sprints/{id}/target")
    public ResponseEntity<ErrorMessage> updateSprintTarget(@PathVariable UUID id,
            @Valid @RequestBody UpdateSprintTargetRequest request) {
        try {
            sprintService.updateSprintTarget(id, request.getStoryPointTarget());
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @GetMapping("/sprints")
    public ResponseEntity<?> getAllSprints() {
        var sprints = sprintService.getAllSprints();
        return ResponseEntity.status(HttpStatus.OK).body(sprints);
    }

    @PostMapping("/students/upload")
    public ResponseEntity<ErrorMessage> uploadStudentData(@Valid @RequestBody StudentUploadRequest request) {
        try {
            studentService.uploadStudentData(request);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalArgumentException | AlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PatchMapping("/deliverables/{id}")
    public ResponseEntity<?> updateDeliverable(@PathVariable UUID id,
            @RequestBody UpdateDeliverableRequest request) {
        try {
            var updatedDeliverable = deliverableService.updateDeliverable(id, request);
            return ResponseEntity.status(HttpStatus.OK).body(updatedDeliverable);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/publish")
    public ResponseEntity<?> publishSystem() {
        try {
            systemStateService.publishSystem();
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }
}
