package com.senior.spm.entity;

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
@Table(name = "committee_professor", uniqueConstraints = {
    @UniqueConstraint(name = "uq_committee_professor_committee_professor", columnNames = {"committee_id", "professor_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class CommitteeProfessor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "committee_id", nullable = false, foreignKey = @ForeignKey(name = "fk_committee_professor_committee"))
    private Committee committee;

    @ManyToOne
    @JoinColumn(name = "professor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_committee_professor_professor"))
    private StaffUser professor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfessorRole role;

    public enum ProfessorRole {
        ADVISOR, JURY
    }
}