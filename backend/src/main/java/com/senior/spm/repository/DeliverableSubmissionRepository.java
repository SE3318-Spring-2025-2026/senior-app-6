package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.Deliverable;

/**
 * Spring Data JPA repository for DeliverableSubmission entity.
 * Provides CRUD operations and custom query methods for submissions.
 */
@Repository
public interface DeliverableSubmissionRepository extends JpaRepository<DeliverableSubmission, UUID> {

    /**
     * Finds all submissions for a given group.
     * 
     * @param group the project group
     * @return list of deliverable submissions for the group
     */
    List<DeliverableSubmission> findByGroup(ProjectGroup group);

    /**
     * Finds all submissions for a given deliverable.
     * 
     * @param deliverable the deliverable
     * @return list of deliverable submissions for the deliverable
     */
    List<DeliverableSubmission> findByDeliverable(Deliverable deliverable);

    /**
     * Finds the latest submission for a group and deliverable.
     * 
     * @param group the project group
     * @param deliverable the deliverable
     * @return the latest submission if exists
     */
    DeliverableSubmission findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(ProjectGroup group, Deliverable deliverable);

    /**
     * Checks if a submission exists for a group and deliverable.
     * 
     * @param group the project group
     * @param deliverable the deliverable
     * @return true if submission exists
     */
    boolean existsByGroupAndDeliverable(ProjectGroup group, Deliverable deliverable);
}
