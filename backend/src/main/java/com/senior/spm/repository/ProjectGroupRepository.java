package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;

@Repository
public interface ProjectGroupRepository extends JpaRepository<ProjectGroup, UUID> {

    boolean existsByGroupNameAndTermId(String groupName, String termId);

    Optional<ProjectGroup> findByIdAndStatus(UUID id, GroupStatus status);

    List<ProjectGroup> findByTermId(String termId);

    List<ProjectGroup> findByTermIdAndStatus(String termId, GroupStatus status);

    long countByAdvisorIdAndTermIdAndStatusNot(UUID advisorId, String termId, GroupStatus status);

    List<ProjectGroup> findByTermIdAndStatusNotAndAdvisorIsNull(String termId, GroupStatus status);

    List<ProjectGroup> findByTermIdAndStatusInAndAdvisorIsNull(String termId, List<GroupStatus> statuses);
}
