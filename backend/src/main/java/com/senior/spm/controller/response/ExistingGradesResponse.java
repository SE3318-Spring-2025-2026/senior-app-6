package com.senior.spm.controller.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExistingGradesResponse {

    private List<GradeEntry> grades;

    @Getter
    @AllArgsConstructor
    public static class GradeEntry {
        private UUID criterionId;
        private String selectedGrade;
    }
}
