package com.senior.spm.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.SprintGroupOverview;
import com.senior.spm.controller.response.SprintOverviewResponse;
import com.senior.spm.controller.response.SprintRefreshResponse;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScrumGradeRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.service.SprintTrackingOrchestrator;
import com.senior.spm.service.TermConfigService;
import com.senior.spm.service.dto.SprintRefreshStats;

import lombok.RequiredArgsConstructor;

/**
 * Coordinator-facing sprint management endpoints (P5 sub-process 5.7).
 *
 * <p>Separate from {@link CoordinatorController} to keep the base controller
 * focused. Both share the same {@code /api/coordinator} base path — Spring
 * merges them correctly.
 *
 * <p>Auth: Staff JWT with role=Coordinator — enforced by
 * {@code SecurityConfig} ({@code /api/coordinator/**} → COORDINATOR).
 */
@RestController
@RequestMapping("/api/coordinator")
@RequiredArgsConstructor
public class CoordinatorSprintController {

    private final SprintTrackingOrchestrator    orchestrator;
    private final SprintRepository              sprintRepository;
    private final SprintTrackingLogRepository   sprintTrackingLogRepository;
    private final ScrumGradeRepository          scrumGradeRepository;
    private final ProjectGroupRepository        projectGroupRepository;
    private final TermConfigService             termConfigService;

    /**
     * Manually triggers the full sprint tracking pipeline (5.1–5.4) for all
     * {@code TOOLS_BOUND} and {@code ADVISOR_ASSIGNED} groups.
     *
     * <p>Deletes all existing {@code SprintTrackingLog} rows and re-fetches from
     * scratch — idempotent (running twice yields the same result).
     *
     * <p>{@code ?force=true} bypasses the sprint-end-date guard, allowing a
     * mid-sprint coordinator override. Omitting the param or sending
     * {@code force=false} returns 400 if the sprint has not yet ended.
     *
     * @param sprintId target sprint
     * @param force    when {@code true}, runs regardless of sprint end date
     * @return 200 with {@link SprintRefreshResponse}
     *
     *         Error responses:
     *         - 400: sprint has not ended and {@code force} was not set to {@code true}
     *         - 404: sprint not found
     */
    @PostMapping("/sprints/{sprintId}/refresh")
    public ResponseEntity<SprintRefreshResponse> refreshSprint(
            @PathVariable UUID sprintId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {

        SprintRefreshStats stats = orchestrator.triggerForSprint(sprintId, force);

        SprintRefreshResponse response = SprintRefreshResponse.builder()
                .sprintId(sprintId)
                .groupsProcessed(stats.groupsProcessed())
                .issuesFetched(stats.issuesFetched())
                .aiValidationsRun(stats.aiValidationsRun())
                .triggeredAt(LocalDateTime.now(ZoneId.of("UTC")))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns a high-level summary of ALL eligible groups' sprint tracking and
     * grading status. Coordinator sees every group — not filtered to any advisor.
     *
     * @param sprintId target sprint
     * @return 200 with {@link SprintOverviewResponse}
     *
     *         Error responses:
     *         - 404: sprint not found
     */
    @GetMapping("/sprints/{sprintId}/overview")
    public ResponseEntity<SprintOverviewResponse> getSprintOverview(@PathVariable UUID sprintId) {
        sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));

        String termId = termConfigService.getActiveTermId();
        List<ProjectGroup> groups = projectGroupRepository.findByTermIdAndStatusIn(
                termId, List.of(GroupStatus.TOOLS_BOUND, GroupStatus.ADVISOR_ASSIGNED));

        List<SprintTrackingLog> allLogs = sprintTrackingLogRepository.findBySprintId(sprintId);
        Map<UUID, List<SprintTrackingLog>> logsByGroup = allLogs.stream()
                .collect(Collectors.groupingBy(log -> log.getGroup().getId()));

        List<SprintGroupOverview> overviews = new ArrayList<>(groups.size());

        for (ProjectGroup group : groups) {
            List<SprintTrackingLog> groupLogs = logsByGroup.getOrDefault(group.getId(), List.of());

            int totalIssues   = groupLogs.size();
            int mergedPRs     = (int) groupLogs.stream().filter(l -> Boolean.TRUE.equals(l.getPrMerged())).count();
            int aiPassCount   = countAi(groupLogs, AiValidationResult.PASS);
            int aiWarnCount   = countAi(groupLogs, AiValidationResult.WARN);
            int aiFailCount   = countAi(groupLogs, AiValidationResult.FAIL);
            int aiPendingCount = countAi(groupLogs, AiValidationResult.PENDING);
            int aiSkippedCount = countAi(groupLogs, AiValidationResult.SKIPPED);

            Optional<ScrumGrade> gradeOpt = scrumGradeRepository.findByGroupIdAndSprintId(
                    group.getId(), sprintId);

            String advisorName = group.getAdvisor() != null ? group.getAdvisor().getMail() : null;

            SprintGroupOverview overview = SprintGroupOverview.builder()
                    .groupId(group.getId())
                    .groupName(group.getGroupName())
                    .advisorName(advisorName)
                    .totalIssues(totalIssues)
                    .mergedPRs(mergedPRs)
                    .aiPassCount(aiPassCount)
                    .aiWarnCount(aiWarnCount)
                    .aiFailCount(aiFailCount)
                    .aiPendingCount(aiPendingCount)
                    .aiSkippedCount(aiSkippedCount)
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
    }

    private int countAi(List<SprintTrackingLog> logs, AiValidationResult result) {
        // Counts across both aiPrResult and aiDiffResult columns
        return (int) logs.stream()
                .filter(l -> result == l.getAiPrResult() || result == l.getAiDiffResult())
                .count();
    }
}
