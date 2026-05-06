package com.senior.spm.controller.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RubricMappingResponse {

    private UUID submissionId;
    private List<Mapping> mappings;

    @Getter
    @AllArgsConstructor
    public static class Mapping {
        private UUID criterionId;
        private String sectionKey;
        private int sectionStart;
        private int sectionEnd;
    }
}
