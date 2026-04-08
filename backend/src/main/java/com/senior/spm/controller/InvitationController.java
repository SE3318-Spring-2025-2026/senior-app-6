package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.dto.InvitationResponse;
import com.senior.spm.controller.dto.RespondInvitationRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// TODO: Issue #45 — [Backend] Invitation Lifecycle Services & Controller
// This skeleton was scaffolded as part of Issue #40 (Base API Layer).
// Full service wiring, business rules, and transactional logic belong to Issue #45.
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Validated
public class InvitationController {

    /**
     * Get all pending invitations for the authenticated student.
     * Auth: Student JWT
     * GET /api/invitations/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<InvitationResponse>> getPendingInvitations() {
        // TODO: wire to InvitationService.getPendingInvitations(extractStudentUUIDFromJWT())
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Accept or decline an invitation.
     * Auth: Student JWT (must be the invitee)
     * PATCH /api/invitations/{invitationId}/respond
     * Returns GroupDetailResponse on accept, InvitationResponse on decline
     */
    @PatchMapping("/{invitationId}/respond")
    public ResponseEntity<?> respondToInvitation(
        @PathVariable UUID invitationId,
        @Valid @RequestBody RespondInvitationRequest request
    ) {
        // TODO: wire to InvitationService.respondToInvitation(invitationId, extractStudentUUIDFromJWT(), request.getAccept())
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Cancel a pending invitation.
     * Auth: Student JWT (must be TEAM_LEADER of the inviting group)
     * DELETE /api/invitations/{invitationId}
     */
    @DeleteMapping("/{invitationId}")
    public ResponseEntity<InvitationResponse> cancelInvitation(
        @PathVariable UUID invitationId
    ) {
        // TODO: wire to InvitationService.cancelInvitation(invitationId, extractStudentUUIDFromJWT())
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
