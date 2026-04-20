package com.senior.spm.controller.response;

import java.util.UUID;

import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeProfessorResponse {

    private UUID id;
    private UUID professorId;
    private String professorName;
    private String professorMail;
    private ProfessorRole role;
}