package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.ScrumGrade;

@Repository
public interface ScrumGradeRepository extends JpaRepository<ScrumGrade, UUID> {

    Optional<ScrumGrade> findByGroupIdAndSprintId(UUID groupId, UUID sprintId);

    List<ScrumGrade> findByAdvisorIdAndSprintId(UUID advisorId, UUID sprintId);

    List<ScrumGrade> findBySprintId(UUID sprintId);

    boolean existsByGroupIdAndSprintId(UUID groupId, UUID sprintId);
}
