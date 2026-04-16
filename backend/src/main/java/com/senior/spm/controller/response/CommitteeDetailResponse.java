package com.senior.spm.controller.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeDetailResponse {

    private UUID id;
    private String committeeName;
    private String termId;
    private UUID deliverableId;
    private List<CommitteeProfessorResponse> professors;
    private List<CommitteeGroupResponse> groups;
}