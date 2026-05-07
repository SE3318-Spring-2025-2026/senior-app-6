package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a submitted deliverable document for a group.
 * Stores the markdown content, submission timestamps, and tracks revisions.
 */
@Entity
@Table(name = "deliverable_submission",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_group_deliverable",
        columnNames = {"group_id", "deliverable_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DeliverableSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ds_group"))
    private ProjectGroup group;

    @ManyToOne
    @JoinColumn(name = "deliverable_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ds_deliverable"))
    private Deliverable deliverable;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String markdownContent;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = true)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isRevision = false;

    @Column(nullable = false)
    private int revisionNumber = 0;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RubricMapping> rubricMappings;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionComment> comments;
}
