package com.senior.spm.controller.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupSummaryResponse {
    private UUID id;
    private String groupName;
    private String termId;
    private String status;
    private int memberCount;
    private boolean jiraBound;
    private boolean githubBound;
}
