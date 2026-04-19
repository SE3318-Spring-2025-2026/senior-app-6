package com.senior.spm.controller.response;

import java.util.UUID;

import com.senior.spm.entity.CommitteeProfessor.CommitteeRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfessorAssignmentResponse {
    private UUID professorId;
    private String mail;
    private CommitteeRole role;
}