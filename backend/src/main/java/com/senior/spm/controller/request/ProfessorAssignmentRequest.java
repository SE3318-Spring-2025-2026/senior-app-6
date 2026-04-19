package com.senior.spm.controller.request;

import java.util.UUID;

import com.senior.spm.entity.CommitteeProfessor.CommitteeRole;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfessorAssignmentRequest {

    @NotNull(message = "Professor id is required")
    private UUID professorId;

    @NotNull(message = "Role is required")
    @Enumerated(EnumType.STRING)
    private CommitteeRole role;
}