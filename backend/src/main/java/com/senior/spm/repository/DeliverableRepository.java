package com.senior.spm.repository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.Deliverable;

@Repository
public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {

    @Query("""
           SELECT COALESCE(SUM(d.weight), 0)
           FROM Deliverable d
           WHERE d.termId = :termId
             AND d.id <> :deliverableId
           """)
    BigDecimal sumWeightsExcludingDeliverableInTerm(
            @Param("termId") UUID termId,
            @Param("deliverableId") UUID deliverableId);
}