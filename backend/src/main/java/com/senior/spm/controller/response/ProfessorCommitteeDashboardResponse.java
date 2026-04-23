package com.senior.spm.controller.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.RubricCriterion;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfessorCommitteeDashboardResponse {

    private UUID committeeId;
    private String committeeName;
    private String termId;
    private ProfessorRole professorRole;

    private UUID deliverableId;
    private String deliverableName;
    private Deliverable.DeliverableType deliverableType;
    private LocalDateTime submissionDeadline;
    private LocalDateTime reviewDeadline;
    private BigDecimal deliverableWeight;

    private List<GroupItem> groups;
    private List<RubricItem> rubrics;

    @Data
    @AllArgsConstructor
    public static class GroupItem {
        private UUID groupId;
        private String groupName;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class RubricItem {
        private String criterionName;
        private RubricCriterion.GradingType gradingType;
        private BigDecimal weight;
    }
}