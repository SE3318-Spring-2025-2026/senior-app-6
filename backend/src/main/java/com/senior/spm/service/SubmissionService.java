package com.senior.spm.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.response.SubmissionCommentResponse;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.Submission;
import com.senior.spm.entity.SubmissionComment;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.CommitteeProfessorRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.SubmissionCommentRepository;
import com.senior.spm.repository.SubmissionRepository;
import com.senior.spm.util.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionCommentRepository submissionCommentRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final CommitteeProfessorRepository committeeProfessorRepository;

    @Transactional(readOnly = true)
    public List<SubmissionCommentResponse> getSubmissionComments(UUID submissionId, Authentication auth) {
        // Find the submission
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));

        // Check authorization
        UUID userId = SecurityUtils.extractPrincipalUUID(auth);
        String role = extractRole(auth);

        if (!hasAccessToSubmission(submission, userId, role)) {
            throw new ForbiddenException("You do not have permission to view comments for this submission");
        }

        // Get comments and map to DTOs
        List<SubmissionComment> comments = submissionCommentRepository.findBySubmissionId(submissionId);
        return comments.stream()
                .map(this::mapToResponse)
                .toList();
    }

    private boolean hasAccessToSubmission(Submission submission, UUID userId, String role) {
        ProjectGroup group = submission.getGroup();

        if ("STUDENT".equals(role)) {
            // Check if student is a member of the group
            return groupMembershipRepository.findByGroupIdAndStudentId(group.getId(), userId).isPresent();
        } else if ("PROFESSOR".equals(role) || "COORDINATOR".equals(role) || "ADMIN".equals(role)) {
            // Check if professor is assigned to a committee that includes this group
            return committeeProfessorRepository.findByProfessorId(userId).stream()
                    .anyMatch(cp -> cp.getCommittee().getGroups().contains(group));
        }

        return false;
    }

    private String extractRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .filter(a -> a instanceof SimpleGrantedAuthority)
                .map(a -> (SimpleGrantedAuthority) a)
                .map(SimpleGrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5)) // Remove "ROLE_" prefix
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No role found in authentication"));
    }

    private SubmissionCommentResponse mapToResponse(SubmissionComment comment) {
        StaffUser author = comment.getAuthor();
        String authorName = author.getMail();

        return new SubmissionCommentResponse(
                comment.getId(),
                comment.getSubmission().getId(),
                author.getId(),
                authorName,
                comment.getContent(),
                comment.getSectionReference(),
                comment.getCreatedAt()
        );
    }
}