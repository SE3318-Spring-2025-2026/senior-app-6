package com.senior.spm.service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.ProjectGroup.GroupStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdvisorRespondResponse {
    private UUID requestId;
    private RequestStatus status;
    private UUID groupId;
    private GroupStatus groupStatus;
}