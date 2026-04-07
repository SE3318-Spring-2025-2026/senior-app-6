package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;

@Repository
public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {

    Optional<GroupMembership> findByStudentId(UUID studentId);

    Optional<GroupMembership> findByGroupIdAndStudentId(UUID groupId, UUID studentId);

    boolean existsByStudentId(UUID studentId);

    List<GroupMembership> findByGroupId(UUID groupId);

    Optional<GroupMembership> findByGroupIdAndRole(UUID groupId, MemberRole role);

    long countByGroupId(UUID groupId);

    void deleteByGroupId(UUID groupId);
}
