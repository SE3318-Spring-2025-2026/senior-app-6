package com.senior.spm.controller.request;

import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddProfessorToCommitteeRequest {

    @NotNull(message = "Professor ID is required")
    private java.util.UUID professorId;

    @NotNull(message = "Role is required")
    private ProfessorRole role;
}