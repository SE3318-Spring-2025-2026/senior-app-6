package com.senior.spm.controller.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignCommitteeProfessorsRequest {

    @NotEmpty(message = "Professors list cannot be empty")
    @Valid
    private List<ProfessorAssignmentRequest> professors;
}