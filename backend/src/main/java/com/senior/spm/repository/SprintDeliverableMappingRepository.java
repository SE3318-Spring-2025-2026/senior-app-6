package com.senior.spm.repository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.SprintDeliverableMapping;

@Repository
public interface SprintDeliverableMappingRepository extends JpaRepository<SprintDeliverableMapping, UUID> {

    boolean existsBySprint_IdAndDeliverable_Id(UUID sprintId, UUID deliverableId);

    @Query("""
           SELECT COALESCE(SUM(m.contributionPercentage), 0)
           FROM SprintDeliverableMapping m
           WHERE m.deliverable.id = :deliverableId
           """)
    BigDecimal sumContributionPercentageByDeliverableId(@Param("deliverableId") UUID deliverableId);
}