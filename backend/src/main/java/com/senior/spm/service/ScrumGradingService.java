package com.senior.spm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.response.ActiveSprintResponse;
import com.senior.spm.controller.response.AdvisorGroupSprintSummaryResponse;
import com.senior.spm.controller.response.PerStudentSummaryResponse;
import com.senior.spm.controller.response.ScrumGradeResponse;
import com.senior.spm.controller.response.SprintTrackingResponse;
import com.senior.spm.controller.response.TrackingIssueResponse;
import com.senior.spm.controller.request.ScrumGradeRequest;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScrumGradeRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScrumGradingService {

    private final ProjectGroupRepository projectGroupRepository;
    private final SprintRepository sprintRepository;
    private final ScrumGradeRepository scrumGradeRepository;
    private final SprintTrackingLogRepository sprintTrackingLogRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final TermConfigService termConfigService;

    // -------------------------------------------------------------------------
    // Active Sprint
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ActiveSprintResponse getActiveSprint() {
        LocalDate today = LocalDate.now();
        Sprint sprint = sprintRepository
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today)
                .stream()
                .max(Comparator.comparing(Sprint::getStartDate))
                .orElseThrow(() -> new NotFoundException("No active sprint found for the current term"));

        return ActiveSprintResponse.builder()
                .sprintId(sprint.getId())
                .startDate(sprint.getStartDate())
                .endDate(sprint.getEndDate())
                .storyPointTarget(sprint.getStoryPointTarget())
                .daysRemaining(ChronoUnit.DAYS.between(today, sprint.getEndDate()))
                .build();
    }

    // -------------------------------------------------------------------------
    // Scrum Grade — Submit (upsert)
    // -------------------------------------------------------------------------

    @Transactional
    public ScrumGrade submitGrade(UUID advisorId, UUID groupId, UUID sprintId, ScrumGradeRequest request) {
        ProjectGroup group = findGroupOrThrow(groupId);
        enforceAdvisorOwnership(group, advisorId);
        Sprint sprint = findSprintOrThrow(sprintId);

        Optional<ScrumGrade> existing = scrumGradeRepository.findByGroupIdAndSprintId(groupId, sprintId);
        if (existing.isEmpty()) {
            ScrumGrade grade = new ScrumGrade();
            grade.setGroup(group);
            grade.setSprint(sprint);
            grade.setAdvisor(group.getAdvisor());
            grade.setPointAGrade(request.getPointAGrade());
            grade.setPointBGrade(request.getPointBGrade());
            grade.setGradedAt(LocalDateTime.now());
            return scrumGradeRepository.save(grade);
        }

        ScrumGrade grade = existing.get();
        grade.setPointAGrade(request.getPointAGrade());
        grade.setPointBGrade(request.getPointBGrade());
        grade.setUpdatedAt(LocalDateTime.now());
        return scrumGradeRepository.save(grade);
    }

    // -------------------------------------------------------------------------
    // Scrum Grade — Get
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ScrumGrade getGrade(UUID advisorId, UUID groupId, UUID sprintId) {
        ProjectGroup group = findGroupOrThrow(groupId);
        enforceAdvisorOwnership(group, advisorId);
        findSprintOrThrow(sprintId);

        return scrumGradeRepository.findByGroupIdAndSprintId(groupId, sprintId)
                .orElseThrow(() -> new NotFoundException("No grade submitted yet for this group and sprint"));
    }

    // -------------------------------------------------------------------------
    // Advisor — Group summaries for a sprint
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdvisorGroupSprintSummaryResponse> getAdvisorGroupSummaries(UUID advisorId, UUID sprintId, boolean includeDisbanded) {
        findSprintOrThrow(sprintId);

        String termId = termConfigService.getActiveTermId();
        List<ProjectGroup> groups = projectGroupRepository.findByAdvisor_IdAndTermId(advisorId, termId)
                .stream()
                .filter(g -> includeDisbanded || g.getStatus() != GroupStatus.DISBANDED)
                .toList();

        // Batch-fetch all tracking logs and grades for the sprint in 2 queries (avoids N+1)
        Map<UUID, List<SprintTrackingLog>> logsByGroup = sprintTrackingLogRepository
                .findBySprintId(sprintId)
                .stream()
                .collect(Collectors.groupingBy(l -> l.getGroup().getId()));

        Map<UUID, ScrumGrade> gradeByGroup = scrumGradeRepository
                .findBySprintId(sprintId)
                .stream()
                .collect(Collectors.toMap(g -> g.getGroup().getId(), g -> g));

        return groups.stream()
                .map(group -> buildGroupSummary(group,
                        logsByGroup.getOrDefault(group.getId(), List.of()),
                        gradeByGroup.get(group.getId())))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Advisor — Per-group tracking detail
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SprintTrackingResponse getAdvisorGroupTracking(UUID advisorId, UUID groupId, UUID sprintId) {
        ProjectGroup group = findGroupOrThrow(groupId);
        enforceAdvisorOwnership(group, advisorId);
        findSprintOrThrow(sprintId);

        List<SprintTrackingLog> logs = sprintTrackingLogRepository.findByGroupIdAndSprintId(groupId, sprintId);

        if (logs.isEmpty()) {
            throw new NotFoundException("No tracking data found — sprint may not have been processed yet");
        }

        return SprintTrackingResponse.builder()
                .groupId(groupId)
                .sprintId(sprintId)
                .fetchedAt(latestFetchedAt(logs))
                .issues(toIssueResponses(logs))
                .perStudentSummary(buildPerStudentSummary(logs))
                .build();
    }

    // -------------------------------------------------------------------------
    // Student — Group tracking detail
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SprintTrackingResponse getStudentGroupTracking(UUID studentId, UUID groupId, UUID sprintId) {
        if (groupMembershipRepository.findByGroupIdAndStudentId(groupId, studentId).isEmpty()) {
            throw new ForbiddenException("You are not a member of this group");
        }
        findSprintOrThrow(sprintId);

        List<SprintTrackingLog> logs = sprintTrackingLogRepository.findByGroupIdAndSprintId(groupId, sprintId);

        return SprintTrackingResponse.builder()
                .groupId(groupId)
                .sprintId(sprintId)
                .fetchedAt(latestFetchedAt(logs))
                .issues(toIssueResponses(logs))
                .build();
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    public ScrumGradeResponse toGradeResponse(ScrumGrade grade) {
        return ScrumGradeResponse.builder()
                .gradeId(grade.getId())
                .groupId(grade.getGroup().getId())
                .sprintId(grade.getSprint().getId())
                .pointAGrade(grade.getPointAGrade())
                .pointBGrade(grade.getPointBGrade())
                .advisorId(grade.getAdvisor().getId())
                .gradedAt(grade.getGradedAt())
                .updatedAt(grade.getUpdatedAt())
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Sprint findSprintOrThrow(UUID sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
    }

    private ProjectGroup findGroupOrThrow(UUID groupId) {
        return projectGroupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
    }

    private LocalDateTime latestFetchedAt(List<SprintTrackingLog> logs) {
        return logs.stream()
                .map(SprintTrackingLog::getFetchedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private void enforceAdvisorOwnership(ProjectGroup group, UUID advisorId) {
        if (group.getAdvisor() == null || !group.getAdvisor().getId().equals(advisorId)) {
            throw new ForbiddenException("You are not the advisor for this group");
        }
    }

    /** Pre-fetched variant — used by getAdvisorGroupSummaries to avoid N+1. */
    private AdvisorGroupSprintSummaryResponse buildGroupSummary(
            ProjectGroup group, List<SprintTrackingLog> logs, ScrumGrade grade) {
        return buildGroupSummaryFromData(group, logs, Optional.ofNullable(grade));
    }

    private AdvisorGroupSprintSummaryResponse buildGroupSummary(ProjectGroup group, UUID sprintId) {
        List<SprintTrackingLog> logs = sprintTrackingLogRepository
                .findByGroupIdAndSprintId(group.getId(), sprintId);
        Optional<ScrumGrade> grade = scrumGradeRepository
                .findByGroupIdAndSprintId(group.getId(), sprintId);
        return buildGroupSummaryFromData(group, logs, grade);
    }

    private AdvisorGroupSprintSummaryResponse buildGroupSummaryFromData(
            ProjectGroup group, List<SprintTrackingLog> logs, Optional<ScrumGrade> grade) {

        long mergedPRs = logs.stream().filter(l -> Boolean.TRUE.equals(l.getPrMerged())).count();

        long aiPassCount = logs.stream().filter(l -> worstForIssue(l) == AiValidationResult.PASS).count();
        long aiWarnCount = logs.stream().filter(l -> worstForIssue(l) == AiValidationResult.WARN).count();
        long aiFailCount = logs.stream().filter(l -> worstForIssue(l) == AiValidationResult.FAIL).count();
        long aiPendingCount = logs.stream().filter(l -> worstForIssue(l) == AiValidationResult.PENDING).count();
        long aiSkippedCount = logs.stream().filter(l -> worstForIssue(l) == AiValidationResult.SKIPPED).count();

        return AdvisorGroupSprintSummaryResponse.builder()
                .groupId(group.getId())
                .groupName(group.getGroupName())
                .totalIssues(logs.size())
                .mergedPRs(mergedPRs)
                .aiPassCount(aiPassCount)
                .aiWarnCount(aiWarnCount)
                .aiFailCount(aiFailCount)
                .aiPendingCount(aiPendingCount)
                .aiSkippedCount(aiSkippedCount)
                .gradeSubmitted(grade.isPresent())
                .pointAGrade(grade.map(ScrumGrade::getPointAGrade).orElse(null))
                .pointBGrade(grade.map(ScrumGrade::getPointBGrade).orElse(null))
                .perStudentSummary(buildPerStudentSummary(logs))
                .build();
    }

    private List<TrackingIssueResponse> toIssueResponses(List<SprintTrackingLog> logs) {
        return logs.stream()
                .map(l -> TrackingIssueResponse.builder()
                        .issueKey(l.getIssueKey())
                        .assigneeGithubUsername(l.getAssigneeGithubUsername())
                        .storyPoints(l.getStoryPoints())
                        .prNumber(l.getPrNumber())
                        .prMerged(l.getPrMerged())
                        .aiPrResult(l.getAiPrResult())
                        .aiDiffResult(l.getAiDiffResult())
                        .build())
                .toList();
    }

    private List<PerStudentSummaryResponse> buildPerStudentSummary(List<SprintTrackingLog> logs) {
        Map<String, List<SprintTrackingLog>> byAssignee = logs.stream()
                .filter(l -> l.getAssigneeGithubUsername() != null)
                .collect(Collectors.groupingBy(SprintTrackingLog::getAssigneeGithubUsername));

        return byAssignee.entrySet().stream()
                .map(e -> {
                    int completedPoints = e.getValue().stream()
                            .filter(l -> Boolean.TRUE.equals(l.getPrMerged()))
                            .mapToInt(l -> l.getStoryPoints() != null ? l.getStoryPoints() : 0)
                            .sum();
                    return PerStudentSummaryResponse.builder()
                            .assigneeGithubUsername(e.getKey())
                            .completedPoints(completedPoints)
                            .aiValidationStatus(worstForStudent(e.getValue()))
                            .build();
                })
                .toList();
    }

    // AI priority: FAIL > WARN > SKIPPED > PASS > PENDING
    private static final Map<AiValidationResult, Integer> AI_PRIORITY = Map.of(
            AiValidationResult.FAIL, 4,
            AiValidationResult.WARN, 3,
            AiValidationResult.SKIPPED, 2,
            AiValidationResult.PASS, 1,
            AiValidationResult.PENDING, 0
    );

    private AiValidationResult worstAiResult(AiValidationResult r1, AiValidationResult r2) {
        return AI_PRIORITY.get(r1) >= AI_PRIORITY.get(r2) ? r1 : r2;
    }

    private AiValidationResult worstForIssue(SprintTrackingLog log) {
        AiValidationResult pr   = log.getAiPrResult()   != null ? log.getAiPrResult()   : AiValidationResult.PENDING;
        AiValidationResult diff = log.getAiDiffResult() != null ? log.getAiDiffResult() : AiValidationResult.PENDING;
        return worstAiResult(pr, diff);
    }

    private AiValidationResult worstForStudent(List<SprintTrackingLog> issues) {
        return issues.stream()
                .map(this::worstForIssue)
                .reduce(AiValidationResult.PENDING, this::worstAiResult);
    }
}
