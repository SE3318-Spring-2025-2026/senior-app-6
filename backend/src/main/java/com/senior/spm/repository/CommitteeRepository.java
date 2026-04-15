package com.senior.spm.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.Committee;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, UUID> {

    @Query("SELECT COUNT(c) > 0 FROM Committee c JOIN c.professors cp WHERE cp.professor.id = :professorId AND c.deliverable.id = :deliverableId")
    boolean existsByProfessorIdAndDeliverableId(@Param("professorId") UUID professorId, @Param("deliverableId") UUID deliverableId);

    @Query("SELECT COUNT(c) > 0 FROM Committee c JOIN c.groups g WHERE g.id = :groupId AND c.deliverable.id = :deliverableId")
    boolean existsByGroupIdAndDeliverableId(@Param("groupId") UUID groupId, @Param("deliverableId") UUID deliverableId);
}