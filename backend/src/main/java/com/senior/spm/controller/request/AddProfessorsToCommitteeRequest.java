package com.senior.spm.controller.request;

import java.util.List;
import java.util.UUID;

import org.hibernate.validator.constraints.UniqueElements;

import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddProfessorsToCommitteeRequest {

    @NotEmpty
    @UniqueElements
    private List<ProfessorEntry> professors;

    @Data
    public static class ProfessorEntry {

        @NotNull
        private UUID professorId;

        @NotNull
        private ProfessorRole role;
    }
}
