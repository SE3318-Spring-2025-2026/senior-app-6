package com.senior.spm.entity;

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
    @JoinColumn(name = "submission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rubric_mapping_submission"))
    private Submission submission;

    @ManyToOne
    @JoinColumn(name = "criterion_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rubric_mapping_criterion"))
    private RubricCriterion criterion;

    @Column(length = 255, nullable = false)
    private String sectionKey;
}
