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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "rubric_grade",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_rg_submission_criterion_reviewer",
        columnNames = {"submission_id", "criterion_id", "reviewer_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class RubricGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rg_submission"))
    private DeliverableSubmission submission;

    @ManyToOne
    @JoinColumn(name = "criterion_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rg_criterion"))
    private RubricCriterion criterion;

    @ManyToOne
    @JoinColumn(name = "reviewer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rg_reviewer"))
    private StaffUser reviewer;

    @Column(nullable = false)
    private String selectedGrade;

    @Column(nullable = false)
    private LocalDateTime gradedAt;
}
