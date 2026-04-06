package com.senior.spm.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.SprintDeliverableMapping;

@Repository
public interface SprintDeliverableMappingRepository extends JpaRepository<SprintDeliverableMapping, UUID> {

    Optional<SprintDeliverableMapping> findBySprintIdAndDeliverableId(UUID sprintId, UUID deliverableId);

}
