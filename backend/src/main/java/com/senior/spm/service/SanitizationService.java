package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SanitizationService handles the automatic disbanding of unadvised groups
 * once the ADVISOR_ASSOCIATION window closes.
 * 
 * Scheduled job runs periodically to detect newly closed windows.
 * Manual trigger available for coordinators to force sanitization.
 * Gracefully handles OptimisticLockException — skips groups modified concurrently.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SanitizationService {

    private final ScheduleWindowRepository scheduleWindowRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final AdvisorRequestRepository advisorRequestRepository;
    private final TermConfigService termConfigService;

    // Tracks last run time to detect newly closed windows
    private LocalDateTime lastScheduledRun = LocalDateTime.now(ZoneId.of("UTC"));

    /**
     * Scheduled job that runs every 5 minutes to check for newly closed ADVISOR_ASSOCIATION windows.
     * Automatically triggers sanitization for any affected terms without manual intervention.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void runSanitizationIfWindowClosed() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        
        // Find windows that closed since last run
        List<ScheduleWindow> recentlyClosedWindows = scheduleWindowRepository
            .findByTypeAndClosesAtBetween(
                WindowType.ADVISOR_ASSOCIATION,
                lastScheduledRun,
                now
            );

        if (recentlyClosedWindows.isEmpty()) {
            log.debug("No newly closed ADVISOR_ASSOCIATION windows detected");
            lastScheduledRun = now;
            return;
        }

        log.info("Detected {} newly closed ADVISOR_ASSOCIATION window(s)", recentlyClosedWindows.size());

        for (ScheduleWindow window : recentlyClosedWindows) {
            try {
                log.info("Running automatic sanitization for term: {}", window.getTermId());
                runSanitization(window.getTermId(), false);
            } catch (Exception e) {
                log.error("Error during automatic sanitization for term {}", window.getTermId(), e);
                // Continue to next window even if one fails
            }
        }

        lastScheduledRun = now;
    }

    /**
     * Manual trigger for coordinators to sanitize (disband unadvised groups).
     * Requires force=true if the ADVISOR_ASSOCIATION window is still open.
     * 
     * @param force if true, bypasses window check and sanitizes immediately
     * @return SanitizationReport with counts of affected groups/requests
     */
    public SanitizationReport triggerManually(boolean force) {
        String termId = termConfigService.getActiveTermId();
        
        // Check if window is still open
        ScheduleWindow window = scheduleWindowRepository
            .findByTermIdAndType(termId, WindowType.ADVISOR_ASSOCIATION)
            .orElse(null);

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        boolean windowIsActive = window != null && window.getClosesAt().isAfter(now);

        if (windowIsActive && !force) {
            throw new BusinessRuleException(
                "Advisor association window is still active — cannot sanitize early without confirmation"
            );
        }

        log.info("Manual sanitization triggered for term: {} (force={})", termId, force);
        return runSanitization(termId, force);
    }

    /**
     * Core sanitization logic:
     * 1. Find all groups without an advisor (unadvised)
     * 2. Mark them DISBANDED
     * 3. Hard-delete their memberships (frees students)
     * 4. Auto-reject pending advisor requests
     * 
     * Gracefully skips groups that throw OptimisticLockException
     * (indicates concurrent modification by advisor accept).
     * 
     * @param termId the term to sanitize
     * @param force flag (for audit purposes, currently unused)
     * @return report of actions taken
     */
    @Transactional
    public SanitizationReport runSanitization(String termId, boolean force) {
        SanitizationReport report = new SanitizationReport();
        report.setTriggeredAt(LocalDateTime.now(ZoneId.of("UTC")));

        // Find all unadvised groups (status FORMING, TOOLS_PENDING, TOOLS_BOUND with no advisor assigned)
        List<ProjectGroup> unadvisedGroups = projectGroupRepository
            .findByTermIdAndStatusInAndAdvisorIsNull(termId, 
                java.util.List.of(GroupStatus.FORMING, GroupStatus.TOOLS_PENDING, GroupStatus.TOOLS_BOUND));

        log.info("Found {} unadvised groups in term {}", unadvisedGroups.size(), termId);

        for (ProjectGroup group : unadvisedGroups) {
            try {
                // Mark group as DISBANDED
                group.setStatus(GroupStatus.DISBANDED);
                projectGroupRepository.save(group);
                log.debug("Disbanded group: {}", group.getId());

                // Hard-delete all memberships (frees students to rejoin next term)
                long membershipsDeleted = groupMembershipRepository.countByGroupId(group.getId());
                groupMembershipRepository.deleteByGroupId(group.getId());
                report.addDeletedMemberships(membershipsDeleted);
                log.debug("Deleted {} memberships for group: {}", membershipsDeleted, group.getId());

                // Auto-reject all PENDING advisor requests for this group
                long requestsUpdated = advisorRequestRepository.bulkUpdateStatusByGroupId(
                    com.senior.spm.entity.AdvisorRequest.RequestStatus.AUTO_REJECTED,
                    group.getId()
                );
                report.addRejectedRequests(requestsUpdated);
                log.debug("Auto-rejected pending advisor requests for group: {}", group.getId());

                report.incrementDisbandedCount();

            } catch (OptimisticLockingFailureException e) {
                // Group was concurrently modified (likely by advisor accept).
                // Skip this group and continue — it will be re-queried on next run.
                log.warn("Skipping group {} due to concurrent modification (optimizer lock). "
                    + "Will retry on next scheduler tick.", group.getId());
            }
        }

        log.info("Sanitization complete for term {}: {} groups disbanded, "
            + "{} memberships deleted, {} requests auto-rejected",
            termId, report.getDisbandedCount(), report.getDeletedMemberships(),
            report.getAutoRejectedRequests());

        return report;
    }

    /**
     * DTO for sanitization result
     */
    public static class SanitizationReport {
        private int disbandedCount = 0;
        private long deletedMemberships = 0;
        private long autoRejectedRequests = 0;
        private LocalDateTime triggeredAt;

        public void incrementDisbandedCount() {
            this.disbandedCount++;
        }

        public void addDeletedMemberships(long count) {
            this.deletedMemberships += count;
        }

        public void addRejectedRequests(long count) {
            this.autoRejectedRequests += count;
        }

        // Getters
        public int getDisbandedCount() { return disbandedCount; }
        public long getDeletedMemberships() { return deletedMemberships; }
        public long getAutoRejectedRequests() { return autoRejectedRequests; }
        public LocalDateTime getTriggeredAt() { return triggeredAt; }
        public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    }
}
