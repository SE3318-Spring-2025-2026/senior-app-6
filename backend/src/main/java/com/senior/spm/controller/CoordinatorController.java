package com.senior.spm.controller;

import java.util.UUID;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.controller.request.CoordinatorMemberRequest;
import com.senior.spm.controller.request.CreateDeliverableRequest;
import com.senior.spm.controller.request.SprintRequest;
import com.senior.spm.controller.response.GroupSummaryResponse; 
import com.senior.spm.controller.request.StudentUploadRequest;
import com.senior.spm.controller.request.UpdateDeliverableRequest;
import com.senior.spm.controller.request.UpdateSprintTargetRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemState;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemStateRepository;
import com.senior.spm.service.GroupService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final SprintRepository sprintRepository;
    private final DeliverableRepository deliverableRepository;
    private final StudentRepository studentRepository;
    private final SystemStateRepository systemStateRepository;
    private final GroupService groupService;

    public CoordinatorController(
            SprintRepository sprintRepository,
            DeliverableRepository deliverableRepository,
            StudentRepository studentRepository,
            SystemStateRepository systemStateRepository,
            GroupService groupService) {
        this.sprintRepository = sprintRepository;
        this.deliverableRepository = deliverableRepository;
        this.studentRepository = studentRepository;
        this.systemStateRepository = systemStateRepository;
        this.groupService = groupService;
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

    @PatchMapping("/sprints/{id}/target")
    public ResponseEntity<ErrorMessage> updateSprintTarget(@PathVariable UUID id,
            @Valid @RequestBody UpdateSprintTargetRequest request) {
        // Check if sprint exists
        var sprint = sprintRepository.findById(id);
        if (sprint.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Sprint not found with ID: " + id));
        }

        // Retrieve the sprint and update the target
        sprint.get().setStoryPointTarget(request.getStoryPointTarget());
        sprintRepository.save(sprint.get());

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

    // ========== GROUP MANAGEMENT ENDPOINTS ==========

    /**
     * Lists all project groups for the active term.
     * <p>
     * REST Endpoint: {@code GET /api/coordinator/groups}
     * <p>
     * Retrieves a complete list of all project groups in the currently active term with full details.
     * Requires coordinator authentication (Staff JWT with COORDINATOR role enforced by SecurityConfig).
     *
     * @return HTTP 200 with List&lt;{@link GroupDetailResponse}&gt; containing all groups for active term
     *         HTTP 500 if internal server error occurs
     * @throws Exception caught and returns HTTP 500 status if group retrieval fails
     */
    @GetMapping("/groups")
    public ResponseEntity<List<GroupSummaryResponse>> listGroups() {
        try {
            // Updated to call the new summary method per DFD 2.6 requirements
            List<GroupSummaryResponse> groups = groupService.getGroupSummariesForActiveTerm();
            return ResponseEntity.status(HttpStatus.OK).body(groups);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves detailed information for a specific project group.
     * <p>
     * REST Endpoint: {@code GET /api/coordinator/groups/{groupId}}
     * <p>
     * Fetches complete details for a single project group including all members and tool bindings.
     * Requires coordinator authentication (Staff JWT with COORDINATOR role enforced by SecurityConfig).
     *
     * @param groupId the UUID path variable identifying the group to retrieve
     * @return HTTP 200 with {@link GroupDetailResponse} containing complete group information
     *         HTTP 404 with {@link ErrorMessage} if group not found
     *         HTTP 500 if internal server error occurs
     * @throws GroupNotFoundException caught and returns HTTP 404 status if group does not exist
     * @throws Exception caught and returns HTTP 500 status for other unexpected errors
     */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<?> getGroupDetail(@PathVariable UUID groupId) {
        try {
            GroupDetailResponse group = groupService.getGroupDetail(groupId);
            return ResponseEntity.status(HttpStatus.OK).body(group);
        } catch (com.senior.spm.exception.GroupNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Adds or removes a student from a project group (force operation by coordinator).
     * <p>
     * REST Endpoint: {@code PATCH /api/coordinator/groups/{groupId}/members}
     * <p>
     * Allows coordinators to manually add or remove students from groups, bypassing normal group
     * formation workflow. Request validation:
     * <ul>
     *   <li>Validates action is either "ADD" or "REMOVE" (returns 400 if invalid)</li>
     *   <li>For ADD: enforces max team size check, auto-denies competing invitations</li>
     *   <li>For REMOVE: prevents removal of TEAM_LEADER role (returns 400)</li>
     * </ul>
     * <p>
     * Requires coordinator authentication (Staff JWT with COORDINATOR role enforced by SecurityConfig).
     *
     * @param groupId the UUID path variable identifying the target group
     * @param request the {@link CoordinatorMemberRequest} containing studentId and action (ADD|REMOVE)
     * @return HTTP 200 with {@link GroupDetailResponse} on successful add/remove
     *         HTTP 400 with {@link ErrorMessage} if:
     *         - action is invalid (not ADD or REMOVE)
     *         - student already in group or at capacity (for ADD)
     *         - student is TEAM_LEADER (for REMOVE)
     *         - other business rule violations
     *         HTTP 404 with {@link ErrorMessage} if group or student not found
     *         HTTP 500 if internal server error occurs
     * @throws GroupNotFoundException caught and returns HTTP 404 if group or membership not found
     * @throws StudentNotFoundException caught and returns HTTP 404 if student not found
     * @throws ForbiddenException caught and returns HTTP 400 if TEAM_LEADER removal attempted
     * @throws BusinessRuleException caught and returns HTTP 400 for business rule violations
     * @throws Exception caught and returns HTTP 500 for other unexpected errors
     */
    @PatchMapping("/groups/{groupId}/members")
    public ResponseEntity<?> updateGroupMembers(
            @PathVariable UUID groupId,
            @Valid @RequestBody CoordinatorMemberRequest request) {
        try {
            // Validate action
            if (!request.getAction().equals("ADD") && !request.getAction().equals("REMOVE")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorMessage("action must be ADD or REMOVE"));
            }

            GroupDetailResponse result;
            if (request.getAction().equals("ADD")) {
                result = groupService.coordinatorAddStudent(groupId, request.getStudentId());
            } else {
                result = groupService.coordinatorRemoveStudent(groupId, request.getStudentId());
            }

            return ResponseEntity.status(HttpStatus.OK).body(result);
        } catch (com.senior.spm.exception.GroupNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (com.senior.spm.exception.StudentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (com.senior.spm.exception.ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        } catch (com.senior.spm.exception.BusinessRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Disbands a project group with full cascade cleanup.
     * <p>
     * REST Endpoint: {@code PATCH /api/coordinator/groups/{groupId}/disband}
     * <p>
     * Permanently closes a project group and triggers comprehensive cascade operations:
     * <ul>
     *   <li>Sets group status to DISBANDED</li>
     *   <li>Hard-deletes all group memberships to free students</li>
     *   <li>Auto-denies all pending group invitations (P2)</li>
     *   <li>Auto-rejects all pending advisor requests (P3 preparation)</li>
     * </ul>
     * <p>
     * All cascade operations execute atomically within a single transaction (all-or-nothing semantics).
     * Requires coordinator authentication (Staff JWT with COORDINATOR role enforced by SecurityConfig).
     *
     * @param groupId the UUID path variable identifying the group to disband
     * @return HTTP 200 with {@link GroupDetailResponse} showing disbanded group
     *         HTTP 400 with {@link ErrorMessage} if group is already disbanded
     *         HTTP 404 with {@link ErrorMessage} if group not found
     *         HTTP 500 if internal server error occurs
     * @throws GroupNotFoundException caught and returns HTTP 404 if group not found
     * @throws BusinessRuleException caught and returns HTTP 400 if group already disbanded
     * @throws Exception caught and returns HTTP 500 for other unexpected errors
     */
    @PatchMapping("/groups/{groupId}/disband")
    public ResponseEntity<?> disbandGroup(@PathVariable UUID groupId) {
        try {
            GroupDetailResponse result = groupService.disbandGroup(groupId);
            return ResponseEntity.status(HttpStatus.OK).body(result);
        } catch (com.senior.spm.exception.GroupNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (com.senior.spm.exception.BusinessRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
