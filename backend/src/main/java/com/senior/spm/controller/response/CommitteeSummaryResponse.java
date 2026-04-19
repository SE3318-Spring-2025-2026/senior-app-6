package com.senior.spm.controller.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeSummaryResponse {
    private UUID id;
    private String committeeName;
    private String termId;
}