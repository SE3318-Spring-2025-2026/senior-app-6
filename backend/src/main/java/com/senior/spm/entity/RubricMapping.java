package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a link between a section of a deliverable submission
 * and a specific rubric criterion.
 * Enables the committee to associate evaluation criteria with
 * specific sections of the submitted markdown document.
 */
@Entity
@Table(name = "rubric_mapping", uniqueConstraints = {
    @UniqueConstraint(name = "uq_rubric_mapping_submission_section", columnNames = {"submission_id", "section_key"})
})
@Getter
@Setter
@NoArgsConstructor
public class RubricMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rm_submission"))
    private DeliverableSubmission submission;

    @ManyToOne
    @JoinColumn(name = "rubric_criterion_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rm_rubric_criterion"))
    private RubricCriterion rubricCriterion;

    @Column(nullable = false)
    private int sectionStart;

    @Column(nullable = false)
    private int sectionEnd;

    @Column(length = 255)
    private String sectionKey;

    @Column(nullable = false)
    private LocalDateTime mappedAt;

    @PrePersist
    @PreUpdate
    private void validateCriterionMatchesSubmissionDeliverable() {
        if (submission == null || rubricCriterion == null
                || submission.getDeliverable() == null
                || rubricCriterion.getDeliverable() == null) {
            return;
        }
        UUID submissionDeliverableId = submission.getDeliverable().getId();
        UUID criterionDeliverableId = rubricCriterion.getDeliverable().getId();
        if (submissionDeliverableId == null || criterionDeliverableId == null) {
            return;
        }
        if (!submissionDeliverableId.equals(criterionDeliverableId)) {
            throw new IllegalStateException(
                "RubricMapping criterion deliverable ("
                + criterionDeliverableId
                + ") does not match submission deliverable ("
                + submissionDeliverableId + ")");
        }
    }
}
