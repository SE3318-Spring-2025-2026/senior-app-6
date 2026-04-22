package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.ProfessorCommitteeDashboardResponse;
import com.senior.spm.service.CommitteeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/professors")
@RequiredArgsConstructor
public class ProfessorController {

    private final CommitteeService committeeService;

    @GetMapping("/me/committees")
    public ResponseEntity<List<ProfessorCommitteeDashboardResponse>> getMyCommittees() {
        UUID professorId = extractPrincipalUUID();
        return ResponseEntity.ok(committeeService.getCommitteesForProfessor(professorId));
    }

    private UUID extractPrincipalUUID() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) auth.getPrincipal());
    }
}