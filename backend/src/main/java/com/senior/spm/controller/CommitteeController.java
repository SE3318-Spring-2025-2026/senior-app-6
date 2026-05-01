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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.AddGroupsToCommitteeRequest;
import com.senior.spm.controller.request.AddProfessorsToCommitteeRequest;
import com.senior.spm.controller.request.CreateCommitteeRequest;
import com.senior.spm.controller.response.CommitteeSubmissionSummaryResponse;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.ConflictException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.CommitteeService;
import com.senior.spm.service.DeliverableSubmissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/committees")
public class CommitteeController {

    private final CommitteeService committeeService;
    private final DeliverableSubmissionService deliverableSubmissionService;

    public CommitteeController(CommitteeService committeeService,
            DeliverableSubmissionService deliverableSubmissionService) {
        this.committeeService = committeeService;
        this.deliverableSubmissionService = deliverableSubmissionService;
    }

    @PostMapping
    public ResponseEntity<?> createCommittee(@Valid @RequestBody CreateCommitteeRequest request) {
        try {
            var result = committeeService.createCommittee(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getCommittees(@RequestParam(required = false) String termId) {
        var result = committeeService.getCommittees(termId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCommitteeDetail(@PathVariable UUID id) {
        try {
            var result = committeeService.getCommitteeDetails(id);
            return ResponseEntity.ok(result);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }

    @PostMapping("/{id}/professors")
    public ResponseEntity<?> addProfessors(@PathVariable UUID id,
            @Valid @RequestBody AddProfessorsToCommitteeRequest request) {
        try {
            var result = committeeService.addProfessorsToCommittee(id, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (ConflictException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }

    @GetMapping("/{id}/submissions")
    public ResponseEntity<List<CommitteeSubmissionSummaryResponse>> getCommitteeSubmissions(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID requesterUUID = UUID.fromString((String) auth.getPrincipal());
        return ResponseEntity.ok(deliverableSubmissionService.listCommitteeSubmissions(id, requesterUUID));
    }

    @PostMapping("/{id}/groups")
    public ResponseEntity<?> addGroups(@PathVariable UUID id,
            @Valid @RequestBody AddGroupsToCommitteeRequest request) {
        try {
            var result = committeeService.addGroupsToCommittee(id, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (ConflictException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }
    }
}
