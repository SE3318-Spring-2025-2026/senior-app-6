package com.senior.spm.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "committee",
        uniqueConstraints = @UniqueConstraint(name = "uk_committee_term_name", columnNames = {"term_id", "committee_name"})
)
@Getter
@Setter
@NoArgsConstructor
public class Committee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "committee_name", nullable = false)
    private String committeeName;

    @Column(name = "term_id", nullable = false)
    private String termId;

    @OneToMany(mappedBy = "committee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommitteeProfessor> professors = new ArrayList<>();

    @OneToMany(mappedBy = "committee")
    private List<ProjectGroup> groups = new ArrayList<>();
}