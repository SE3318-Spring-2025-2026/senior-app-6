package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;

import lombok.RequiredArgsConstructor;

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
public class SanitizationService {

    private final ScheduleWindowRepository scheduleWindowRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final AdvisorRequestRepository advisorRequestRepository;

    /**
     * Scheduled job that runs every 5 minutes to check for closed ADVISOR_ASSOCIATION windows
     * and automatically disband unadvised groups.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void runSanitizationIfWindowClosed() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        
        // Find all ADVISOR_ASSOCIATION windows that have closed
        List<ScheduleWindow> closedWindows = scheduleWindowRepository
            .findByTypeAndClosesAtLessThan(WindowType.ADVISOR_ASSOCIATION, now);

        for (ScheduleWindow window : closedWindows) {
            try {
                runSanitization(window.getTermId(), false);
            } catch (Exception e) {
                // Continue to next window even if one fails
            }
        }
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
     * @param force flag (for audit purposes)
     */
    @Transactional
    public void runSanitization(String termId, boolean force) {
        // Find all unadvised groups (status FORMING, TOOLS_PENDING, TOOLS_BOUND with no advisor assigned)
        List<ProjectGroup> unadvisedGroups = projectGroupRepository
            .findByTermIdAndStatusInAndAdvisorIsNull(termId, 
                java.util.List.of(GroupStatus.FORMING, GroupStatus.TOOLS_PENDING, GroupStatus.TOOLS_BOUND));

        for (ProjectGroup group : unadvisedGroups) {
            try {
                // Mark group as DISBANDED
                group.setStatus(GroupStatus.DISBANDED);
                projectGroupRepository.save(group);

                // Hard-delete all memberships (frees students to rejoin next term)
                groupMembershipRepository.deleteByGroupId(group.getId());

                // Auto-reject all PENDING advisor requests for this group
                advisorRequestRepository.bulkUpdateStatusByGroupId(
                    com.senior.spm.entity.AdvisorRequest.RequestStatus.AUTO_REJECTED,
                    group.getId()
                );

            } catch (OptimisticLockingFailureException e) {
                // Group was concurrently modified (likely by advisor accept).
                // Skip this group and continue — it will be re-queried on next run.
            }
        }
    }
}
