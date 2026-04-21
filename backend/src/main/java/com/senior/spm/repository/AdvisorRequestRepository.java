package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;

@Repository
public interface AdvisorRequestRepository extends JpaRepository<AdvisorRequest, UUID> {

    List<AdvisorRequest> findByGroupId(UUID groupId);

    List<AdvisorRequest> findByAdvisorId(UUID advisorId);

    List<AdvisorRequest> findByAdvisorIdAndStatus(UUID advisorId, RequestStatus status);

    Optional<AdvisorRequest> findByGroupIdAndStatus(UUID groupId, RequestStatus status);

    Optional<AdvisorRequest> findTopByGroupIdOrderBySentAtDesc(UUID groupId);

    long countByAdvisorIdAndStatus(UUID advisorId, RequestStatus status);

    long countByGroupIdAndStatus(UUID groupId, RequestStatus status);

    @Modifying
    @Query("UPDATE AdvisorRequest ar SET ar.status = ?1 WHERE ar.status = 'PENDING' AND ar.group.id = ?2 AND ar.id != ?3")
    void bulkUpdateStatusForGroup(RequestStatus status, UUID groupId, UUID excludeId);

    @Modifying
    @Query("UPDATE AdvisorRequest ar SET ar.status = :status WHERE ar.status = 'PENDING' AND ar.group.id = :groupId")
    long bulkUpdateStatusByGroupId(@org.springframework.data.repository.query.Param("status") RequestStatus status, @org.springframework.data.repository.query.Param("groupId") UUID groupId);
}
