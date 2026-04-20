package com.senior.spm.controller.request;

import java.util.List;
import java.util.UUID;

import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddProfessorsToCommitteeRequest {

    @NotEmpty
    @Valid
    private List<ProfessorEntry> professors;

    @Data
    public static class ProfessorEntry {

        @NotNull
        private UUID professorId;

        @NotNull
        private ProfessorRole role;
    }
}
