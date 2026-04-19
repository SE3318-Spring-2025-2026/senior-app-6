package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.AssignCommitteeGroupsRequest;
import com.senior.spm.controller.request.AssignCommitteeProfessorsRequest;
import com.senior.spm.controller.request.CreateCommitteeRequest;
import com.senior.spm.controller.response.CommitteeDetailResponse;
import com.senior.spm.controller.response.CommitteeSummaryResponse;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.ProfessorCommitteeSummaryResponse;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.CommitteeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CommitteeController {

    private final CommitteeService committeeService;

    @PostMapping("/api/committees")
    public ResponseEntity<?> createCommittee(@Valid @RequestBody CreateCommitteeRequest request) {
        try {
            CommitteeSummaryResponse response = committeeService.createCommittee(
                    request.getCommitteeName(),
                    request.getTermId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage(e.getMessage()));
        }
    }

    @GetMapping("/api/committees")
    public ResponseEntity<List<CommitteeSummaryResponse>> listCommittees(
            @RequestParam(required = false) String termId
    ) {
        return ResponseEntity.ok(committeeService.listCommittees(termId));
    }

    @GetMapping("/api/committees/{id}")
    public ResponseEntity<?> getCommitteeDetail(@PathVariable UUID id) {
        try {
            CommitteeDetailResponse response = committeeService.getCommitteeDetail(id);
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/api/committees/{id}/professors")
    public ResponseEntity<?> assignProfessors(
            @PathVariable UUID id,
            @Valid @RequestBody AssignCommitteeProfessorsRequest request
    ) {
        try {
            CommitteeDetailResponse response = committeeService.assignProfessors(id, request.getProfessors());
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (BusinessRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/api/committees/{id}/groups")
    public ResponseEntity<?> assignGroups(
            @PathVariable UUID id,
            @Valid @RequestBody AssignCommitteeGroupsRequest request
    ) {
        try {
            CommitteeDetailResponse response = committeeService.assignGroups(id, request.getGroupIds());
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (BusinessRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    @GetMapping("/api/professors/me/committees")
    public ResponseEntity<List<ProfessorCommitteeSummaryResponse>> getMyCommittees() {
        UUID professorId = extractStaffUUIDFromJWT();
        return ResponseEntity.ok(committeeService.getCommitteesForProfessor(professorId));
    }

    private UUID extractStaffUUIDFromJWT() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Authenticated professor information could not be found.");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof String principalText)) {
            throw new IllegalStateException("JWT principal is not in the expected format.");
        }

        try {
            return UUID.fromString(principalText);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT principal is not a valid UUID.");
        }
    }
}