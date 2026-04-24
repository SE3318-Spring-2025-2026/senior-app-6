package com.senior.spm.controller.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public final class GroupDetailResponse implements InvitationActionResponse {

    private UUID id;
    private String groupName;
    private String termId;
    private String status;
    private LocalDateTime createdAt;
    private String jiraSpaceUrl;
    private String jiraEmail;
    private String jiraProjectKey;
    private Boolean jiraBound;
    private String githubOrgName;
    private Boolean githubBound;
    private String githubRepoName;
    private Boolean githubTokenValid;
    private LocalDateTime githubPatExpiresAt;
    private Boolean jiraTokenValid;
    private LocalDate jiraTokenExpiresAt;
    private List<MemberResponse> members;
    private UUID advisorId;
    private String advisorMail;

    @Data
    public static class MemberResponse {
        private String studentId;
        private String role;
        private LocalDateTime joinedAt;
    }
}
