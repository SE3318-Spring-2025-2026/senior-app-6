package com.senior.spm.service.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.senior.spm.entity.ProjectGroup.GroupStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorRequestDetail {
    private UUID requestId;
    private RequestGroupDetail group;
    private LocalDateTime sentAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestGroupDetail {
        private UUID id;
        private String groupName;
        private String termId;
        private GroupStatus status;
        private List<GroupMemberDetail> members;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMemberDetail {
        private String studentId;
        private String role;
        private LocalDateTime joinedAt;
    }
}