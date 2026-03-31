package com.senior.spm.entity;

import java.math.BigDecimal;
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
@Table(name = "sprintDeliverableMapping", uniqueConstraints = {
    @UniqueConstraint(name = "uq_sdm", columnNames = {"sprintId", "deliverableId"})
})
@Getter
@Setter
@NoArgsConstructor
public class SprintDeliverableMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "sprintId", nullable = false, foreignKey = @ForeignKey(name = "fk_sdm_sprint"))
    private Sprint sprint;

    @ManyToOne
    @JoinColumn(name = "deliverableId", nullable = false, foreignKey = @ForeignKey(name = "fk_sdm_deliverable"))
    private Deliverable deliverable;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal contributionPercentage;
}
