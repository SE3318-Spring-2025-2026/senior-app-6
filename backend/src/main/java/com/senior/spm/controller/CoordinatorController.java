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
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.controller.request.CoordinatorMemberRequest;
import com.senior.spm.controller.request.CreateDeliverableRequest;
import com.senior.spm.controller.request.MapDeliverablesRequest;
import com.senior.spm.controller.request.RubricRequest;
import com.senior.spm.controller.request.SprintRequest;
import com.senior.spm.controller.request.StudentUploadRequest;
import com.senior.spm.controller.request.UpdateDeliverableRequest;
import com.senior.spm.controller.request.UpdateDeliverableWeightRequest;
import com.senior.spm.controller.request.UpdateSprintTargetRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.GroupSummaryResponse;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.DeliverableService;
import com.senior.spm.service.GroupService;
import com.senior.spm.service.SanitizationService;
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
    private final SanitizationService sanitizationService;

    public CoordinatorController(SprintService sprintService,
            DeliverableService deliverableService,
            StudentService studentService,
            SystemStateService systemStateService,
            GroupService groupService,
            SanitizationService sanitizationService) {
        this.sprintService = sprintService;
        this.deliverableService = deliverableService;
        this.studentService = studentService;
        this.systemStateService = systemStateService;
        this.groupService = groupService;
        this.sanitizationService = sanitizationService;
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
     * Lists all project groups for the active term.
     * REST Endpoint: {@code GET /api/coordinator/groups}
     */
    @GetMapping("/groups")
    public ResponseEntity<List<GroupSummaryResponse>> listGroups() {
        try {
            List<GroupSummaryResponse> groups = groupService.getGroupSummariesForActiveTerm();
            return ResponseEntity.status(HttpStatus.OK).body(groups);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves detailed information for a specific project group.
     * REST Endpoint: {@code GET /api/coordinator/groups/{groupId}}
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
     * Adds or removes a student from a project group (coordinator force operation).
     * REST Endpoint: {@code PATCH /api/coordinator/groups/{groupId}/members}
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
     * Disbands a project group with full cascade cleanup.
     * REST Endpoint: {@code PATCH /api/coordinator/groups/{groupId}/disband}
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

    /**
     * Automatic sanitization — disband all unadvised groups (without advisors)
     * trigger to mark groups DISBANDED and hard-delete their memberships.
     * 
     * Requires force=true if ADVISOR_ASSOCIATION window is still open.
     * Returns counts of affected groups and advisor requests.
     * 
     * REST Endpoint: {@code POST /api/coordinator/sanitize}
     * Auth: Coordinator (staff role)
     */
    @PostMapping("/sanitize")
    public ResponseEntity<?> sanitizeUnadvisedGroups(
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        try {
            boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
            SanitizationService.SanitizationReport report = sanitizationService.triggerManually(force);
            return ResponseEntity.status(HttpStatus.OK).body(report);
        } catch (com.senior.spm.exception.BusinessRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorMessage("Sanitization failed: " + e.getMessage()));
        }
    }
}
