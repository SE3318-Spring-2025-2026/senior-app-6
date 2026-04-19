package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.CommitteeProfessor;

@Repository
public interface CommitteeProfessorRepository extends JpaRepository<CommitteeProfessor, UUID> {

    List<CommitteeProfessor> findByCommitteeId(UUID committeeId);

    List<CommitteeProfessor> findByProfessor_Id(UUID professorId);

    boolean existsByProfessor_IdAndCommittee_TermIdAndCommittee_IdNot(UUID professorId, String termId, UUID committeeId);

    boolean existsByProfessor_IdAndCommittee_TermId(UUID professorId, String termId);

    void deleteByCommitteeId(UUID committeeId);
}