package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.Committee;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, UUID> {

    boolean existsByCommitteeNameAndTermId(String committeeName, String termId);

    List<Committee> findByTermId(String termId);

    @EntityGraph(attributePaths = {"professors", "professors.professor", "groups"})
    @Query("select c from Committee c where c.id = :id")
    Optional<Committee> findDetailedById(@Param("id") UUID id);
}