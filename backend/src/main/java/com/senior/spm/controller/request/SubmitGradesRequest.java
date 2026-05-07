package com.senior.spm.controller.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitGradesRequest {

    @NotEmpty(message = "grades must not be empty")
    @Valid
    private List<GradeEntry> grades;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GradeEntry {

        @NotNull(message = "criterionId must not be null")
        private UUID criterionId;

        @NotBlank(message = "selectedGrade must not be blank")
        private String selectedGrade;
    }
}
