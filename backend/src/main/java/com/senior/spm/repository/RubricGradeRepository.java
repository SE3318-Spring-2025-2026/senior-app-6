package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.RubricGrade;

@Repository
public interface RubricGradeRepository extends JpaRepository<RubricGrade, UUID> {

    List<RubricGrade> findBySubmissionId(UUID submissionId);

    Optional<RubricGrade> findBySubmissionIdAndCriterionIdAndReviewerId(
            UUID submissionId, UUID criterionId, UUID reviewerId);

    @Query("SELECT COUNT(rg) > 0 FROM RubricGrade rg WHERE rg.submission.id = :submissionId AND rg.reviewer.id = :reviewerId")
    boolean existsBySubmissionAndReviewer(@Param("submissionId") UUID submissionId, @Param("reviewerId") UUID reviewerId);
}
