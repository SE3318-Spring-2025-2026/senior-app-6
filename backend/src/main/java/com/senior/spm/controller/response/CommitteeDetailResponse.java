package com.senior.spm.controller.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeDetailResponse {
    private UUID id;
    private String committeeName;
    private String termId;
    private List<ProfessorAssignmentResponse> professors;
    private List<UUID> groupIds;
}