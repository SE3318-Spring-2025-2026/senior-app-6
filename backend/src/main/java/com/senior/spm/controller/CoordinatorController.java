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

import com.senior.spm.controller.request.AddRubricRequest;
import com.senior.spm.controller.request.CreateDeliverableRequest;
import com.senior.spm.controller.request.MapDeliverablesRequest;
import com.senior.spm.controller.request.SprintRequest;
import com.senior.spm.controller.request.StudentUploadRequest;
import com.senior.spm.controller.request.UpdateDeliverableRequest;
import com.senior.spm.controller.request.UpdateDeliverableWeightRequest;
import com.senior.spm.controller.request.UpdateSprintTargetRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintDeliverableMapping;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemState;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.SprintDeliverableMappingRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemStateRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final SprintRepository sprintRepository;
    private final DeliverableRepository deliverableRepository;
    private final StudentRepository studentRepository;
    private final SystemStateRepository systemStateRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final SprintDeliverableMappingRepository sprintDeliverableMappingRepository;

    public CoordinatorController(SprintRepository sprintRepository,
            DeliverableRepository deliverableRepository,
            StudentRepository studentRepository,
            SystemStateRepository systemStateRepository,
            RubricCriterionRepository rubricCriterionRepository,
            SprintDeliverableMappingRepository sprintDeliverableMappingRepository) {
        this.sprintRepository = sprintRepository;
        this.deliverableRepository = deliverableRepository;
        this.studentRepository = studentRepository;
        this.systemStateRepository = systemStateRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
        this.sprintDeliverableMappingRepository = sprintDeliverableMappingRepository;
    }

    @PostMapping("/sprints")
    public ResponseEntity<?> createSprints(@Valid @RequestBody SprintRequest request) {
        // Validate that endDate is not before startDate
        if (request.getEndDate().isBefore(request.getStartDate())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("End date cannot be before start date"));
        }

        // Create and save the sprint
        Sprint sprint = new Sprint();
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());

        Sprint savedSprint = sprintRepository.save(sprint);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedSprint);
    }

    @PostMapping("/deliverables")
    public ResponseEntity<?> createDeliverable(@Valid @RequestBody CreateDeliverableRequest request) {
        // Validate that reviewDeadline is not before submissionDeadline
        if (request.getReviewDeadline().isBefore(request.getSubmissionDeadline())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("Review deadline cannot be before submission deadline"));
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

    @PatchMapping("/deliverables/{id}/weight")
    public ResponseEntity<?> updateDeliverableWeight(@PathVariable UUID id,
            @Valid @RequestBody UpdateDeliverableWeightRequest request) {
        var deliverable = deliverableRepository.findById(id);
        if (deliverable.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Deliverable not found with ID: " + id));
        }

        Deliverable existingDeliverable = deliverable.get();
        existingDeliverable.setWeight(request.getWeightPercentage());
        Deliverable updatedDeliverable = deliverableRepository.save(existingDeliverable);

        return ResponseEntity.status(HttpStatus.OK).body(updatedDeliverable);
    }

    @PostMapping("/deliverables/{id}/rubric")
    public ResponseEntity<?> addRubricToDeliverable(@PathVariable UUID id,
            @Valid @RequestBody AddRubricRequest request) {
        var deliverable = deliverableRepository.findById(id);
        if (deliverable.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorMessage("Deliverable not found with ID: " + id));
        }

        var rubricCriterion = new RubricCriterion();
        rubricCriterion.setDeliverable(deliverable.get());
        rubricCriterion.setCriterionName(request.getCriterionName());
        rubricCriterion.setGradingType(request.getGradingType());
        rubricCriterion.setWeight(request.getWeightPercentage());
        rubricCriterion.setDeliverable(deliverable.get());
        rubricCriterionRepository.save(rubricCriterion);

        return ResponseEntity.status(HttpStatus.CREATED).body(rubricCriterion);
    }

    @PostMapping("/sprints/{id}/deliverable-mapping")
    public ResponseEntity<?> mapDeliverablesToSprint(@PathVariable UUID id,
            @RequestBody MapDeliverablesRequest request) {
        var sprint = sprintRepository.findById(id);
        if (sprint.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorMessage("Sprint not found with ID: " + id));
        }

        var deliverable = deliverableRepository.findById(request.getDeliverableId());
        if (deliverable.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorMessage("Deliverable not found with ID: " + request.getDeliverableId()));
        }
		
        var mapping = sprintDeliverableMappingRepository.findBySprintIdAndDeliverableId(id, request.getDeliverableId());
        if (!mapping.isEmpty()) {
            var existingMapping = mapping.get();
            existingMapping.setContributionPercentage(request.getContributionPercentage());
            sprintDeliverableMappingRepository.save(existingMapping);
        } else {
            var newMapping = new SprintDeliverableMapping();
            newMapping.setSprint(sprint.get());
            newMapping.setDeliverable(deliverable.get());
            newMapping.setContributionPercentage(request.getContributionPercentage());
            sprintDeliverableMappingRepository.save(newMapping);
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PatchMapping("/sprints/{id}/target")
    public ResponseEntity<ErrorMessage> updateSprintTarget(@PathVariable UUID id,
            @Valid @RequestBody UpdateSprintTargetRequest request) {
        var sprint = sprintRepository.findById(id);
        if (sprint.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Sprint not found with ID: " + id));
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/students/upload")
    public ResponseEntity<ErrorMessage> uploadStudentData(@Valid @RequestBody StudentUploadRequest request) {
        if (request.getStudentIds().size() != request.getStudentIds().stream().distinct().count()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("Duplicate student IDs found in the request"));
        }

        for (String studentId : request.getStudentIds()) {
            if (studentRepository.existsByStudentId(studentId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorMessage("Id " + studentId + " already exists in the database"));
            }
        }

        var students = request.getStudentIds().stream().map(id -> {
            Student student = new Student();
            student.setStudentId(id);
            return student;
        }).toList();

        if (students.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("No valid student IDs provided"));
        }

        studentRepository.saveAll(students);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/deliverables/{id}")
    public ResponseEntity<?> updateDeliverable(@PathVariable UUID id,
            @RequestBody UpdateDeliverableRequest request) {
        // Check if deliverable exists
        var deliverable = deliverableRepository.findById(id);
        if (deliverable.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Deliverable not found with ID: " + id));
        }

        // Update non-null fields
        Deliverable existingDeliverable = deliverable.get();
        if (request.getName() != null) {
            existingDeliverable.setName(request.getName());
        }
        if (request.getType() != null) {
            existingDeliverable.setType(request.getType());
        }
        if (request.getSubmissionDeadline() != null) {
            existingDeliverable.setSubmissionDeadline(request.getSubmissionDeadline());
        }
        if (request.getReviewDeadline() != null) {
            existingDeliverable.setReviewDeadline(request.getReviewDeadline());
        }
        if (request.getWeight() != null) {
            existingDeliverable.setWeight(request.getWeight());
        }

        // Save and return the updated deliverable
        Deliverable updatedDeliverable = deliverableRepository.save(existingDeliverable);
        return ResponseEntity.status(HttpStatus.OK).body(updatedDeliverable);
    }

    @PostMapping("/publish")
    public ResponseEntity<?> publishSystem() {
        // Validate that students exist
        if (studentRepository.count() == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("Incomplete configuration: Missing students"));
        }

        // Validate that sprints exist
        if (sprintRepository.count() == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("Incomplete configuration: Missing sprints"));
        }

        // Validate that deliverables exist
        if (deliverableRepository.count() == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("Incomplete configuration: Missing deliverables"));
        }

        // Get or create the system state
        SystemState systemState;
        if (systemStateRepository.count() == 0) {
            systemState = new SystemState();
            systemState.setStatus(SystemState.Status.ACTIVE);
        } else {
            systemState = systemStateRepository.findAll().get(0);
            systemState.setStatus(SystemState.Status.ACTIVE);
        }

        // Save the updated system state
        systemStateRepository.save(systemState);

        return ResponseEntity.status(HttpStatus.OK).build();

    }
}
