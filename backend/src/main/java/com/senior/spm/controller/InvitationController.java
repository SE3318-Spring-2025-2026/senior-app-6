package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.InvitationActionResponse;
import com.senior.spm.controller.response.InvitationResponse;
import com.senior.spm.controller.request.RespondInvitationRequest;
import com.senior.spm.service.InvitationService;
import com.senior.spm.util.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for invitation-owned lifecycle endpoints.
 *
 * <p>Group-owned invitation endpoints live in {@link GroupController}; this
 * controller handles the authenticated student's invitation inbox, responses,
 * and invitation cancellation by the inviting team leader.
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Validated
public class InvitationController {

    private final InvitationService invitationService;

    /**
     * Get all pending invitations for the authenticated student.
     * Auth: Student JWT
     * GET /api/invitations/pending
     *
     * @return {@link ResponseEntity} with status 200 and the authenticated student's pending invitations
     */
    @GetMapping("/pending")
    public ResponseEntity<List<InvitationResponse>> getPendingInvitations(Authentication auth) {
        List<InvitationResponse> response = invitationService.getPendingInvitations(SecurityUtils.extractPrincipalUUID(auth));
        return ResponseEntity.ok(response);
    }

    /**
     * Accept or decline an invitation.
     * Auth: Student JWT (must be the invitee)
     * PATCH /api/invitations/{invitationId}/respond
     * Returns GroupDetailResponse on accept, InvitationResponse on decline
     *
     * @param invitationId UUID of the invitation being answered
     * @param request request body that declares whether the invitation is accepted
     * @return {@link ResponseEntity} with status 200 and either the updated invitation or the new group detail
     * @throws com.senior.spm.exception.InvitationNotFoundException if the invitation does not exist
     * @throws com.senior.spm.exception.ForbiddenException if the invitation belongs to another student
     * @throws com.senior.spm.exception.InvitationNotPendingException if the invitation is already terminal
     * @throws com.senior.spm.exception.BusinessRuleException if accept is blocked by status or roster rules
     */
    @PatchMapping("/{invitationId}/respond")
    public ResponseEntity<InvitationActionResponse> respondToInvitation(
        @PathVariable UUID invitationId,
        @Valid @RequestBody RespondInvitationRequest request,
        Authentication auth
    ) {
        InvitationActionResponse response = invitationService.respondToInvitation(
            invitationId,
            SecurityUtils.extractPrincipalUUID(auth),
            request.getAccept()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a pending invitation.
     * Auth: Student JWT (must be TEAM_LEADER of the inviting group)
     * DELETE /api/invitations/{invitationId}
     *
     * @param invitationId UUID of the invitation that should be cancelled
     * @return {@link ResponseEntity} with status 200 and the updated invitation summary
     * @throws com.senior.spm.exception.InvitationNotFoundException if the invitation does not exist
     * @throws com.senior.spm.exception.ForbiddenException if the caller is not the inviting group's leader
     * @throws com.senior.spm.exception.InvitationNotPendingException if the invitation is already terminal
     */
    @DeleteMapping("/{invitationId}")
    public ResponseEntity<InvitationResponse> cancelInvitation(
        @PathVariable UUID invitationId,
        Authentication auth
    ) {
        InvitationResponse response = invitationService.cancelInvitation(invitationId, SecurityUtils.extractPrincipalUUID(auth));
        return ResponseEntity.ok(response);
    }
}
