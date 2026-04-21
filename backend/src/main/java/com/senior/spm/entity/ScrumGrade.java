package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    name = "scrum_grade",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sg_group_sprint",
        columnNames = {"group_id", "sprint_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class ScrumGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sg_group"))
    private ProjectGroup group;

    @ManyToOne
    @JoinColumn(name = "sprint_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sg_sprint"))
    private Sprint sprint;

    @ManyToOne
    @JoinColumn(name = "advisor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sg_advisor"))
    private StaffUser advisor;

    @JsonProperty("pointA_grade")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScrumGradeValue pointAGrade;

    @JsonProperty("pointB_grade")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScrumGradeValue pointBGrade;

    @Column(nullable = false)
    private LocalDateTime gradedAt;

    @Column
    private LocalDateTime updatedAt;
}
