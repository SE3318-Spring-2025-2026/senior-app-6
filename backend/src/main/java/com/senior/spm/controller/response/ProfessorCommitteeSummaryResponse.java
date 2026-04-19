package com.senior.spm.controller.response;

import java.util.List;
import java.util.UUID;

import com.senior.spm.entity.CommitteeProfessor.CommitteeRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfessorCommitteeSummaryResponse {
    private UUID committeeId;
    private String committeeName;
    private CommitteeRole role;
    private List<CommitteeGroupSummaryResponse> groups;
}