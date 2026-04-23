package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.AdvisorOverrideRequest;
import com.senior.spm.controller.request.CoordinatorMemberRequest;
import com.senior.spm.controller.request.CreateDeliverableRequest;
import com.senior.spm.controller.request.MapDeliverablesRequest;
import com.senior.spm.controller.request.RubricRequest;
import com.senior.spm.controller.request.SprintRequest;
import com.senior.spm.controller.request.StudentUploadRequest;
import com.senior.spm.controller.request.UpdateDeliverableRequest;
import com.senior.spm.controller.request.UpdateDeliverableWeightRequest;
import com.senior.spm.controller.request.UpdateSprintTargetRequest;
import com.senior.spm.controller.response.AdvisorCapacityResponse;
import com.senior.spm.controller.response.AdvisorOverrideResponse;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.GroupDetailResponse;
import com.senior.spm.controller.response.GroupSummaryResponse;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.AdvisorService;
import com.senior.spm.service.DeliverableService;
import com.senior.spm.service.GroupService;
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
    private final GroupService groupService;
    private final AdvisorService advisorService;

    public CoordinatorController(SprintService sprintService,
            DeliverableService deliverableService,
            StudentService studentService,
            SystemStateService systemStateService,
            GroupService groupService,
            AdvisorService advisorService) {
        this.sprintService = sprintService;
        this.deliverableService = deliverableService;
        this.studentService = studentService;
        this.systemStateService = systemStateService;
        this.groupService = groupService;
        this.advisorService = advisorService;
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

    @GetMapping("/deliverables/{id}/rubric")
    public ResponseEntity<?> getRubricsForDeliverable(@PathVariable UUID id) {
        try {
            var criteria = deliverableService.getRubricsForDeliverable(id);
            return ResponseEntity.status(HttpStatus.OK).body(criteria);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/deliverables/{id}/rubric")
    public ResponseEntity<?> updateRubricsForDeliverable(@PathVariable UUID id,
            @Valid @RequestBody RubricRequest request) {
        try {
            var updatedCriteria = deliverableService.updateRubricsForDeliverable(id, request.getCriteria());
            return ResponseEntity.status(HttpStatus.OK).body(updatedCriteria);
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

    // ========== GROUP MANAGEMENT ENDPOINTS (P2) ==========
    /**
     * Lists all project groups for the active term. REST Endpoint:
     * {@code GET /api/coordinator/groups}
     */
    @GetMapping("/groups")
    public ResponseEntity<List<GroupSummaryResponse>> listGroups(
            @RequestParam(required = false) String termId
    ) {
        try {
            List<GroupSummaryResponse> groups = groupService.getGroupSummaries(termId);
            return ResponseEntity.status(HttpStatus.OK).body(groups);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves detailed information for a specific project group. REST
     * Endpoint: {@code GET /api/coordinator/groups/{groupId}}
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
     * Adds or removes a student from a project group (coordinator force
     * operation). REST Endpoint:
     * {@code PATCH /api/coordinator/groups/{groupId}/members}
     */
    @PatchMapping("/groups/{groupId}/members")
    public ResponseEntity<?> updateGroupMembers(
            @PathVariable UUID groupId,
            @Valid @RequestBody CoordinatorMemberRequest request) {
        try {
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
     * Disbands a project group with full cascade cleanup. REST Endpoint:
     * {@code PATCH /api/coordinator/groups/{groupId}/disband}
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

    // ========== P3 ADVISOR OVERRIDE ENDPOINTS ==========
    /**
     * Lists all professors with their current group assignment count and
     * capacity for the active term. Unlike the student-facing
     * {@code GET /api/advisors}, this includes advisors who are at or above
     * capacity and adds the {@code atCapacity} flag.
     *
     * <p>
     * termId is resolved server-side via
     * {@link com.senior.spm.service.TermConfigService} — never passed from the
     * client.
     *
     * <p>
     * Auth: Staff JWT with role=Coordinator (enforced by SecurityConfig).
     *
     * <p>
     * REST Endpoint: {@code GET /api/coordinator/advisors}
     * <p>
     * Sequence: DFD 3.5 / sequence 3.5_coordinator_advisor_p3.md
     *
     * @return 200 with list of all professors and capacity metadata
     */
    @GetMapping("/advisors")
    public ResponseEntity<List<AdvisorCapacityResponse>> listAdvisorsWithCapacity() {
        List<AdvisorCapacityResponse> advisors = advisorService.getAllAdvisorsWithCapacity();
        return ResponseEntity.ok(advisors);
    }

    /**
     * Coordinator force-assigns or force-removes an advisor for a group,
     * bypassing both the schedule window and the advisor capacity limit.
     *
     * <p>
     * Action {@code ASSIGN}:
     * <ul>
     * <li>Requires {@code advisorId} in the body → 400 if absent.</li>
     * <li>Returns 400 if group is DISBANDED.</li>
     * <li>Returns 400 if group status is not TOOLS_BOUND or
     * ADVISOR_ASSIGNED.</li>
     * <li>Returns 400 if the group already has this exact advisor
     * assigned.</li>
     * <li>Atomically sets {@code group.advisorId}, sets
     * {@code group.status = ADVISOR_ASSIGNED}, and bulk AUTO_REJECTs all
     * PENDING advisor requests for the group.</li>
     * </ul>
     *
     * <p>
     * Action {@code REMOVE}:
     * <ul>
     * <li>{@code advisorId} in the body is ignored.</li>
     * <li>Returns 400 if the group has no advisor assigned.</li>
     * <li>Clears {@code group.advisorId}, sets
     * {@code group.status = TOOLS_BOUND}.</li>
     * </ul>
     *
     * <p>
     * Auth: Staff JWT with role=Coordinator (enforced by SecurityConfig).
     *
     * <p>
     * REST Endpoint: {@code PATCH /api/coordinator/groups/{groupId}/advisor}
     * <p>
     * Sequence: DFD 3.5 / sequence 3.5_coordinator_advisor_p3.md
     *
     * @param groupId UUID of the target group
     * @param request {@link AdvisorOverrideRequest} with {@code action} and
     * optional {@code advisorId}
     * @return 200 with {@link AdvisorOverrideResponse} containing groupId,
     * updated status, and advisorId
     *
     * Error responses: - 400: advisorId absent for ASSIGN; group DISBANDED;
     * invalid status; already assigned; no advisor to remove - 404: group not
     * found; advisor not found
     */
    @PatchMapping("/groups/{groupId}/advisor")
    public ResponseEntity<?> overrideAdvisor(
            @PathVariable UUID groupId,
            @Valid @RequestBody AdvisorOverrideRequest request) {
        if ("ASSIGN".equals(request.getAction())) {
            if (request.getAdvisorId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorMessage("advisorId is required for ASSIGN action"));
            }
            AdvisorOverrideResponse result = advisorService.assignAdvisor(groupId, request.getAdvisorId());
            return ResponseEntity.ok(result);
        } else {
            // REMOVE — advisorId is ignored
            AdvisorOverrideResponse result = advisorService.removeAdvisor(groupId);
            return ResponseEntity.ok(result);
        }
    }
}