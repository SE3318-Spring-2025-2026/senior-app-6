package com.senior.spm.entity;

import java.math.BigDecimal;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rubricCriterion")
@Getter
@Setter
@NoArgsConstructor
public class RubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "deliverableId", nullable = false, foreignKey = @ForeignKey(name = "fk_rc_deliverable"))
    private Deliverable deliverable;

    @Column(length = 255, nullable = false)
    private String criterionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GradingType gradingType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    public enum GradingType {
        Binary, Soft
    }
}
