package com.senior.spm.entity;

import java.math.BigDecimal;
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
    name = "final_grade",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_fg_student",
        columnNames = {"student_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class FinalGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fg_student"))
    private Student student;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fg_group"))
    private ProjectGroup group;

    @Column(precision = 10, scale = 4)
    private BigDecimal weightedTotal;

    @Column(precision = 10, scale = 4)
    private BigDecimal completionRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal finalGrade;

    @Column
    private LocalDateTime calculatedAt;
}
