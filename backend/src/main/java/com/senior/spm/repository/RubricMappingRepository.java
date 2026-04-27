package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.RubricMapping;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.RubricCriterion;

/**
 * Spring Data JPA repository for RubricMapping entity.
 * Provides CRUD operations and custom query methods for rubric mappings.
 */
@Repository
public interface RubricMappingRepository extends JpaRepository<RubricMapping, UUID> {

    /**
     * Finds all rubric mappings for a given submission.
     * 
     * @param submission the deliverable submission
     * @return list of rubric mappings for the submission
     */
    List<RubricMapping> findBySubmission(DeliverableSubmission submission);

    /**
     * Finds all rubric mappings for a given rubric criterion.
     * 
     * @param rubricCriterion the rubric criterion
     * @return list of rubric mappings for the criterion
     */
    List<RubricMapping> findByRubricCriterion(RubricCriterion rubricCriterion);

    /**
     * Finds a rubric mapping for a submission and criterion.
     * 
     * @param submission the deliverable submission
     * @param rubricCriterion the rubric criterion
     * @return the rubric mapping if exists
     */
    RubricMapping findBySubmissionAndRubricCriterion(DeliverableSubmission submission, RubricCriterion rubricCriterion);

    /**
     * Checks if a mapping exists for a submission and criterion.
     * 
     * @param submission the deliverable submission
     * @param rubricCriterion the rubric criterion
     * @return true if mapping exists
     */
    boolean existsBySubmissionAndRubricCriterion(DeliverableSubmission submission, RubricCriterion rubricCriterion);
}
