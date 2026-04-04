package com.senior.spm.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "deliverable")
@Getter
@Setter
@NoArgsConstructor
public class Deliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliverableType type;

    @Column(nullable = false)
    private LocalDateTime submissionDeadline;

    @Column(nullable = false)
    private LocalDateTime reviewDeadline;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    private UUID termId;

    public enum DeliverableType {
        Proposal, SoW, Demonstration
    }
}