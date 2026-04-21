package com.senior.spm.controller.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommitteeSummaryResponse {

    private UUID id;
    private String committeeName;
    private String termId;
    private UUID deliverableId;
}
