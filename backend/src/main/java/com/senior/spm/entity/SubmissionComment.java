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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents feedback or comments left by a committee member
 * on a deliverable submission.
 * Used by advisors and jury members to provide review feedback.
 */
@Entity
@Table(name = "submission_comment")
@Getter
@Setter
@NoArgsConstructor
public class SubmissionComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sc_submission"))
    private DeliverableSubmission submission;

    @ManyToOne
    @JoinColumn(name = "commenter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sc_commenter"))
    private StaffUser commenter;

    @Lob
    @Column(nullable = false)
    private String commentText;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
