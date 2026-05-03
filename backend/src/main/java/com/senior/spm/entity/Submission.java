package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "submission", uniqueConstraints = {
    @UniqueConstraint(name = "uq_submission_group_deliverable", columnNames = {"group_id", "deliverable_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_submission_group"))
    private ProjectGroup group;

    @ManyToOne
    @JoinColumn(name = "deliverable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_submission_deliverable"))
    private Deliverable deliverable;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String markdownContent;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "submission")
    private List<RubricMapping> rubricMappings;
}
