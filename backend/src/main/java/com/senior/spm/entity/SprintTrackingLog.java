package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "sprint_tracking_log",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_stl_group_sprint_issue",
        columnNames = {"group_id", "sprint_id", "issue_key"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class SprintTrackingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stl_group"))
    private ProjectGroup group;

    @ManyToOne
    @JoinColumn(name = "sprint_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stl_sprint"))
    private Sprint sprint;

    @Column(nullable = false)
    private String issueKey;

    @Column
    private String assigneeGithubUsername;

    @Column
    private Integer storyPoints;

    @Column
    private Long prNumber;

    // null = no matching branch found; false = branch exists but PR is open/unmerged; true = PR merged
    @Column
    private Boolean prMerged;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiValidationResult aiPrResult = AiValidationResult.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiValidationResult aiDiffResult = AiValidationResult.PENDING;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;
}
