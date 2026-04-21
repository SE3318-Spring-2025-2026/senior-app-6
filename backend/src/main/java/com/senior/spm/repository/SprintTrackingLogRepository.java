package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.SprintTrackingLog;

@Repository
public interface SprintTrackingLogRepository extends JpaRepository<SprintTrackingLog, UUID> {

    List<SprintTrackingLog> findByGroupIdAndSprintId(UUID groupId, UUID sprintId);

    List<SprintTrackingLog> findBySprintId(UUID sprintId);

    @Transactional
    void deleteByGroupIdAndSprintId(UUID groupId, UUID sprintId);

    @Transactional
    void deleteBySprintId(UUID sprintId);
}
