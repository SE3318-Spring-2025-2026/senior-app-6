package com.senior.spm.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorRequestSummary {
    private UUID requestId;
    private UUID groupId;
    private String groupName;
    private String termId;
    private int memberCount;
    private LocalDateTime sentAt;
}