package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.response.SubmissionCommentResponse;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.SubmissionComment;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.SubmissionCommentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmissionCommentService {

    private final SubmissionCommentRepository submissionCommentRepository;
    private final DeliverableSubmissionRepository deliverableSubmissionRepository;
    private final StaffUserRepository staffUserRepository;
    private final CommitteeRepository committeeRepository;

    @Transactional
    public SubmissionCommentResponse createComment(
            UUID submissionId,
            UUID reviewerId,
            String commentText) {

        DeliverableSubmission submission = deliverableSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));

        StaffUser reviewer = staffUserRepository.findById(reviewerId)
                .orElseThrow(() -> new NotFoundException("Reviewer not found"));

        UUID groupId = submission.getGroup().getId();
        UUID deliverableId = submission.getDeliverable().getId();

        boolean assignedCommitteeMember = committeeRepository.existsByProfessorIdAndGroupIdAndDeliverableId(
                reviewer.getId(),
                groupId,
                deliverableId
        );

        if (!assignedCommitteeMember) {
            throw new ForbiddenException("Only assigned committee members can comment on this submission");
        }

        SubmissionComment comment = new SubmissionComment();
        comment.setSubmission(submission);
        comment.setCommenter(reviewer);
        comment.setCommentText(commentText);
        comment.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));

        SubmissionComment saved = submissionCommentRepository.save(comment);

        return new SubmissionCommentResponse(
                saved.getId(),
                submission.getId(),
                reviewer.getId(),
                reviewer.getMail(),
                saved.getCommentText(),
                saved.getCreatedAt()
        );
    }

    public List<SubmissionCommentResponse> getComments(UUID submissionId, UUID requesterId) {
        DeliverableSubmission submission = deliverableSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));

        UUID groupId = submission.getGroup().getId();
        UUID deliverableId = submission.getDeliverable().getId();

        // Check if requester is either a group member or assigned committee member
        boolean isGroupMember = submission.getGroup().getMembers().stream()
                .anyMatch(membership -> membership.getStudent().getId().equals(requesterId));

        boolean isCommitteeMember = committeeRepository.existsByProfessorIdAndGroupIdAndDeliverableId(
                requesterId, groupId, deliverableId);

        if (!isGroupMember && !isCommitteeMember) {
            throw new ForbiddenException("Access denied: You are not authorized to view these comments");
        }

        List<SubmissionComment> comments = submissionCommentRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId);

        return comments.stream()
                .map(comment -> new SubmissionCommentResponse(
                        comment.getId(),
                        submission.getId(),
                        comment.getCommenter().getId(),
                        comment.getCommenter().getMail(),
                        comment.getCommentText(),
                        comment.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
}