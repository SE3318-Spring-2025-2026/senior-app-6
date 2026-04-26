package com.senior.spm.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.senior.spm.controller.response.SprintGroupOverview;
import com.senior.spm.controller.response.SprintOverviewResponse;
import com.senior.spm.controller.response.SprintRefreshResponse;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScrumGradeRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.service.AdvisorService;
import com.senior.spm.service.DeliverableService;
import com.senior.spm.service.GroupService;
import com.senior.spm.service.SprintService;
import com.senior.spm.service.SprintTrackingOrchestrator;
import com.senior.spm.service.StudentService;
import com.senior.spm.service.SystemStateService;
import com.senior.spm.service.TermConfigService;
import com.senior.spm.service.dto.SprintRefreshStats;

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
    private final SprintTrackingOrchestrator orchestrator;
    private final SprintRepository sprintRepository;
    private final SprintTrackingLogRepository sprintTrackingLogRepository;
    private final ScrumGradeRepository scrumGradeRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final TermConfigService termConfigService;

    public CoordinatorController(SprintService sprintService,
            DeliverableService deliverableService,
            StudentService studentService,
            SystemStateService systemStateService,
            GroupService groupService,
            AdvisorService advisorService,
            SprintTrackingOrchestrator orchestrator,
            SprintRepository sprintRepository,
            SprintTrackingLogRepository sprintTrackingLogRepository,
            ScrumGradeRepository scrumGradeRepository,
            ProjectGroupRepository projectGroupRepository,
            TermConfigService termConfigService) {
        this.sprintService = sprintService;
        this.deliverableService = deliverableService;
        this.studentService = studentService;
        this.systemStateService = systemStateService;
        this.groupService = groupService;
        this.advisorService = advisorService;
        this.orchestrator = orchestrator;
        this.sprintRepository = sprintRepository;
        this.sprintTrackingLogRepository = sprintTrackingLogRepository;
        this.scrumGradeRepository = scrumGradeRepository;
        this.projectGroupRepository = projectGroupRepository;
        this.termConfigService = termConfigService;
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
    @GetMapping("/groups")
    public ResponseEntity<List<GroupSummaryResponse>> listGroups(
            @RequestParam(required = false) String termId) {
        try {
            List<GroupSummaryResponse> groups = groupService.getGroupSummaries(termId);
            return ResponseEntity.status(HttpStatus.OK).body(groups);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

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
    @GetMapping("/advisors")
    public ResponseEntity<List<AdvisorCapacityResponse>> listAdvisorsWithCapacity() {
        List<AdvisorCapacityResponse> advisors = advisorService.getAllAdvisorsWithCapacity();
        return ResponseEntity.ok(advisors);
    }

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
            AdvisorOverrideResponse result = advisorService.removeAdvisor(groupId);
            return ResponseEntity.ok(result);
        }
    }

    // ========== P5 SPRINT TRACKING ENDPOINTS ==========

    /**
     * Manually triggers the full sprint tracking pipeline (5.1–5.4) for all
     * TOOLS_BOUND and ADVISOR_ASSIGNED groups.
     *
     * <p>{@code ?force=true} bypasses the sprint-end-date guard.
     * Returns 400 if sprint has not ended and force is false. Returns 404 if sprint not found.
     */
    @PostMapping("/sprints/{sprintId}/refresh")
    public ResponseEntity<?> refreshSprint(
            @PathVariable UUID sprintId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            SprintRefreshStats stats = orchestrator.triggerForSprint(sprintId, force);
            SprintRefreshResponse response = SprintRefreshResponse.builder()
                    .sprintId(sprintId)
                    .groupsProcessed(stats.groupsProcessed())
                    .issuesFetched(stats.issuesFetched())
                    .aiValidationsRun(stats.aiValidationsRun())
                    .triggeredAt(LocalDateTime.now(ZoneId.of("UTC")))
                    .build();
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (com.senior.spm.exception.BusinessRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    /**
     * Returns a high-level summary of ALL eligible groups' sprint tracking and
     * grading status. Coordinator sees every group — not filtered to any advisor.
     *
     * <p>Grades are batch-loaded to avoid N+1 queries.
     * Returns 404 if sprint not found.
     */
    @GetMapping("/sprints/{sprintId}/overview")
    public ResponseEntity<?> getSprintOverview(@PathVariable UUID sprintId) {
        try {
            sprintRepository.findById(sprintId)
                    .orElseThrow(() -> new NotFoundException("Sprint not found"));

            String termId = termConfigService.getActiveTermId();
            List<ProjectGroup> groups = projectGroupRepository.findByTermIdAndStatusIn(
                    termId, List.of(GroupStatus.TOOLS_BOUND, GroupStatus.ADVISOR_ASSIGNED));

            // Batch-load all logs and grades for the sprint — avoids N+1 queries
            List<SprintTrackingLog> allLogs = sprintTrackingLogRepository.findBySprintId(sprintId);
            Map<UUID, List<SprintTrackingLog>> logsByGroup = allLogs.stream()
                    .collect(Collectors.groupingBy(log -> log.getGroup().getId()));

            Map<UUID, ScrumGrade> gradeByGroup = scrumGradeRepository.findBySprintId(sprintId)
                    .stream().collect(Collectors.toMap(g -> g.getGroup().getId(), g -> g));

            List<SprintGroupOverview> overviews = new ArrayList<>(groups.size());

            for (ProjectGroup group : groups) {
                List<SprintTrackingLog> groupLogs = logsByGroup.getOrDefault(group.getId(), List.of());
                Optional<ScrumGrade> gradeOpt = Optional.ofNullable(gradeByGroup.get(group.getId()));

                SprintGroupOverview overview = SprintGroupOverview.builder()
                        .groupId(group.getId())
                        .groupName(group.getGroupName())
                        .advisorEmail(group.getAdvisor() != null ? group.getAdvisor().getMail() : null)
                        .totalIssues(groupLogs.size())
                        .mergedPRs((int) groupLogs.stream().filter(l -> Boolean.TRUE.equals(l.getPrMerged())).count())
                        .aiPassCount(countAi(groupLogs, AiValidationResult.PASS))
                        .aiWarnCount(countAi(groupLogs, AiValidationResult.WARN))
                        .aiFailCount(countAi(groupLogs, AiValidationResult.FAIL))
                        .aiPendingCount(countAi(groupLogs, AiValidationResult.PENDING))
                        .aiSkippedCount(countAi(groupLogs, AiValidationResult.SKIPPED))
                        .gradeSubmitted(gradeOpt.isPresent())
                        .pointAGrade(gradeOpt.map(ScrumGrade::getPointAGrade).orElse(null))
                        .pointBGrade(gradeOpt.map(ScrumGrade::getPointBGrade).orElse(null))
                        .build();

                overviews.add(overview);
            }

            return ResponseEntity.ok(SprintOverviewResponse.builder()
                    .sprintId(sprintId)
                    .groups(overviews)
                    .build());

        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    /** Counts across both aiPrResult and aiDiffResult columns independently — no double-count. */
    private int countAi(List<SprintTrackingLog> logs, AiValidationResult result) {
        return (int) logs.stream().filter(l -> result == l.getAiPrResult()).count()
             + (int) logs.stream().filter(l -> result == l.getAiDiffResult()).count();
    }
}
