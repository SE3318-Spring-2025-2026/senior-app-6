package com.senior.spm.service;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.service.dto.GithubFileDiffDto;
import com.senior.spm.service.dto.GithubPrDto;
import com.senior.spm.service.dto.JiraIssueDto;
import com.senior.spm.service.dto.SprintRefreshStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the end-of-sprint tracking pipeline (sub-processes 5.1–5.4).
 *
 * <p>Transaction design (identical pattern to {@link SanitizationService}):
 * <ul>
 *   <li>{@code runDailyTracking} — {@code @Scheduled}, no transaction; calls self-proxy.</li>
 *   <li>{@code processGroupsForSprint} — no transaction; outer loop with per-group try-catch
 *       to ensure one failing group never blocks others.</li>
 *   <li>{@code processGroup} — {@code REQUIRES_NEW}; one commit per group. An
 *       {@code OptimisticLockingFailureException} rolls back only this group.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintTrackingOrchestrator {

    private final SprintRepository              sprintRepository;
    private final ProjectGroupRepository        projectGroupRepository;
    private final SprintTrackingLogRepository   sprintTrackingLogRepository;
    private final JiraSprintService             jiraSprintService;
    private final GithubSprintService           githubSprintService;
    private final AiValidationService           aiValidationService;
    private final TermConfigService             termConfigService;

    /**
     * Self-proxy — injected lazily to avoid circular dependency.
     * Required so that internal calls to {@code processGroup} pass through the
     * Spring AOP proxy, activating its {@code @Transactional(REQUIRES_NEW)}.
     */
    @Autowired
    @Lazy
    private SprintTrackingOrchestrator self;

    // ── scheduled entry point ────────────────────────────────────────────────

    /**
     * Fires daily at 01:00 UTC. Finds all sprints whose {@code endDate} was yesterday
     * and runs the full 5.1–5.4 pipeline for every eligible group.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void runDailyTracking() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
        List<Sprint> endedSprints = sprintRepository.findByEndDate(yesterday);
        if (endedSprints.isEmpty()) {
            return;
        }
        log.info("Daily sprint tracking: {} sprint(s) ended on {}", endedSprints.size(), yesterday);
        for (Sprint sprint : endedSprints) {
            try {
                processGroupsForSprint(sprint);
            } catch (Exception e) {
                log.error("Sprint tracking failed for sprint {}: {}", sprint.getId(), e.getMessage(), e);
            }
        }
    }

    // ── coordinator manual entry point ───────────────────────────────────────

    /**
     * Manual trigger exposed via {@code POST /api/coordinator/sprints/{sprintId}/refresh}.
     *
     * <p>Guard: when {@code force=false} and the sprint has not yet ended,
     * throws {@link BusinessRuleException} (→ HTTP 400).
     *
     * <p>Deletes all existing {@code SprintTrackingLog} rows for the sprint before
     * re-fetching — ensures idempotency (running twice yields the same result).
     *
     * @param sprintId target sprint
     * @param force    when {@code true}, bypasses the sprint-end-date guard
     * @return counts of groups processed, issues fetched, and AI validations run
     * @throws NotFoundException     if the sprint does not exist
     * @throws BusinessRuleException if the sprint has not ended and {@code force=false}
     */
    public SprintRefreshStats triggerForSprint(UUID sprintId, boolean force) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));

        if (!force && !sprint.getEndDate().isBefore(LocalDate.now(ZoneId.of("UTC")))) {
            throw new BusinessRuleException(
                    "Sprint has not ended yet — use ?force=true to refresh early");
        }

        // Wipe existing rows so re-run is idempotent
        sprintTrackingLogRepository.deleteBySprintId(sprintId);

        AtomicInteger groupsProcessed   = new AtomicInteger();
        AtomicInteger issuesFetched     = new AtomicInteger();
        AtomicInteger aiValidationsRun  = new AtomicInteger();

        String termId = termConfigService.getActiveTermId();
        List<ProjectGroup> groups = projectGroupRepository.findByTermIdAndStatusIn(
                termId, List.of(GroupStatus.TOOLS_BOUND, GroupStatus.ADVISOR_ASSIGNED));

        for (ProjectGroup group : groups) {
            try {
                int[] counts = self.processGroup(group, sprint);
                groupsProcessed.incrementAndGet();
                issuesFetched.addAndGet(counts[0]);
                aiValidationsRun.addAndGet(counts[1]);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Skipping group {} — optimistic lock conflict during sprint refresh", group.getId());
            } catch (Exception e) {
                log.error("Error processing group {} during sprint refresh: {}", group.getId(), e.getMessage(), e);
            }
        }

        return new SprintRefreshStats(groupsProcessed.get(), issuesFetched.get(), aiValidationsRun.get());
    }

    // ── per-sprint outer loop ────────────────────────────────────────────────

    private void processGroupsForSprint(Sprint sprint) {
        String termId = termConfigService.getActiveTermId();
        List<ProjectGroup> groups = projectGroupRepository.findByTermIdAndStatusIn(
                termId, List.of(GroupStatus.TOOLS_BOUND, GroupStatus.ADVISOR_ASSIGNED));

        log.info("Sprint {} tracking: {} eligible group(s)", sprint.getId(), groups.size());

        for (ProjectGroup group : groups) {
            try {
                self.processGroup(group, sprint);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Skipping group {} (sprint {}) — optimistic lock conflict; next run will retry",
                        group.getId(), sprint.getId());
            } catch (Exception e) {
                log.error("Error processing group {} for sprint {}: {}",
                        group.getId(), sprint.getId(), e.getMessage(), e);
            }
        }
    }

    // ── per-group atomic unit ────────────────────────────────────────────────

    /**
     * Runs the full 5.1–5.4 pipeline for one group in its own transaction.
     *
     * <p>Returns {@code int[2]}: {@code [issuesFetched, aiValidationsRun]}.
     * Uses {@code REQUIRES_NEW} so a failure rolls back only this group without
     * poisoning any outer session.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] processGroup(ProjectGroup group, Sprint sprint) {
        // 5.1 — fetch JIRA stories (resolves active sprint internally)
        List<JiraIssueDto> issues = jiraSprintService.fetchSprintStories(group);

        // Wipe any pre-existing rows for this group+sprint (handles per-group re-runs)
        sprintTrackingLogRepository.deleteByGroupIdAndSprintId(group.getId(), sprint.getId());

        if (issues.isEmpty()) {
            log.info("No JIRA issues found for group {} sprint {}", group.getId(), sprint.getId());
            return new int[]{0, 0};
        }

        LocalDateTime fetchedAt = LocalDateTime.now(ZoneId.of("UTC"));
        List<SprintTrackingLog> logs = new ArrayList<>(issues.size());

        for (JiraIssueDto issue : issues) {
            SprintTrackingLog entry = new SprintTrackingLog();
            entry.setGroup(group);
            entry.setSprint(sprint);
            entry.setIssueKey(issue.issueKey());
            entry.setAssigneeGithubUsername(issue.assigneeEmail());
            entry.setStoryPoints(issue.storyPoints());
            entry.setFetchedAt(fetchedAt);
            entry.setAiPrResult(AiValidationResult.PENDING);
            entry.setAiDiffResult(AiValidationResult.PENDING);
            logs.add(entry);
        }

        // Persist initial rows before GitHub/AI calls
        List<SprintTrackingLog> savedLogs = sprintTrackingLogRepository.saveAll(logs);

        int aiValidationsRun = 0;

        for (int i = 0; i < savedLogs.size(); i++) {
            SprintTrackingLog log = savedLogs.get(i);
            JiraIssueDto issue = issues.get(i);

            // 5.2 — GitHub branch + PR match
            Optional<String> branchOpt = githubSprintService.findBranchByIssueKey(group, issue.issueKey());
            if (branchOpt.isEmpty()) {
                // No branch found — skip AI, leave prMerged=null, set AI to SKIPPED
                log.setAiPrResult(AiValidationResult.SKIPPED);
                log.setAiDiffResult(AiValidationResult.SKIPPED);
                continue;
            }

            Optional<GithubPrDto> prOpt = githubSprintService.findMergedPR(group, branchOpt.get());
            if (prOpt.isEmpty()) {
                // Branch found but no closed PR
                log.setPrMerged(false);
                log.setAiPrResult(AiValidationResult.SKIPPED);
                log.setAiDiffResult(AiValidationResult.SKIPPED);
                continue;
            }

            GithubPrDto pr = prOpt.get();
            log.setPrNumber(pr.prNumber());
            log.setPrMerged(pr.merged());

            if (!pr.merged()) {
                log.setAiPrResult(AiValidationResult.SKIPPED);
                log.setAiDiffResult(AiValidationResult.SKIPPED);
                continue;
            }

            // 5.3 — AI PR review validation
            List<String> comments = githubSprintService.fetchPRReviewComments(group, pr.prNumber());
            log.setAiPrResult(aiValidationService.validatePRReview(comments));
            aiValidationsRun++;

            // 5.4 — AI diff semantics validation
            List<GithubFileDiffDto> diffs = githubSprintService.fetchFileDiffs(group, pr.prNumber());
            log.setAiDiffResult(aiValidationService.validateIssueDiff(issue.description(), diffs));
            aiValidationsRun++;
        }

        sprintTrackingLogRepository.saveAll(savedLogs);

        return new int[]{savedLogs.size(), aiValidationsRun};
    }
}
