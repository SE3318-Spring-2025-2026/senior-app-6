package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.SubmissionComment;

@Repository
public interface SubmissionCommentRepository extends JpaRepository<SubmissionComment, UUID> {

    List<SubmissionComment> findBySubmissionId(UUID submissionId);
}