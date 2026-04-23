package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.CommitteeProfessor;

@Repository
public interface CommitteeProfessorRepository extends JpaRepository<CommitteeProfessor, UUID> {

    @EntityGraph(attributePaths = {
            "committee",
            "committee.deliverable",
            "committee.groups",
            "professor"
    })
    List<CommitteeProfessor> findByProfessorId(UUID professorId);
}