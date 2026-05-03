package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.SubmissionComment;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.StaffUser;

/**
 * Spring Data JPA repository for SubmissionComment entity.
 * Provides CRUD operations and custom query methods for submission comments.
 */
@Repository
public interface SubmissionCommentRepository extends JpaRepository<SubmissionComment, UUID> {

    /**
     * Finds all comments for a given submission.
     * 
     * @param submission the deliverable submission
     * @return list of comments for the submission
     */
    List<SubmissionComment> findBySubmission(DeliverableSubmission submission);

    /**
     * Finds all comments by a specific commenter on any submission.
     * 
     * @param commenter the staff user who made the comment
     * @return list of comments by the commenter
     */
    List<SubmissionComment> findByCommenter(StaffUser commenter);

    /**
     * Finds all comments by a specific commenter on a submission.
     * 
     * @param submission the deliverable submission
     * @param commenter the staff user who made the comment
     * @return list of comments by the commenter on the submission
     */
    List<SubmissionComment> findBySubmissionAndCommenter(DeliverableSubmission submission, StaffUser commenter);

    /**
     * Counts the number of comments on a submission.
     * 
     * @param submission the deliverable submission
     * @return the number of comments
     */
    long countBySubmission(DeliverableSubmission submission);

    /**
     * Finds all comments for a given submission ID, ordered by creation time.
     * 
     * @param submissionId the submission ID
     * @return list of comments for the submission, ordered by creation time ascending
     */
    List<SubmissionComment> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
