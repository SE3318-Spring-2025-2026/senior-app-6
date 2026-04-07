package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, UUID> {

    List<GroupInvitation> findByGroupId(UUID groupId);

    List<GroupInvitation> findByInviteeId(UUID inviteeId);

    List<GroupInvitation> findByInviteeIdAndStatus(UUID inviteeId, InvitationStatus status);

    boolean existsByGroupIdAndInviteeIdAndStatus(UUID groupId, UUID inviteeId, InvitationStatus status);

    long countByGroupIdAndStatus(UUID groupId, InvitationStatus status);

    @Modifying
    @Query("UPDATE GroupInvitation gi SET gi.status = 'AUTO_DENIED' WHERE gi.status = 'PENDING' AND gi.invitee.id = ?1 AND gi.group.id != ?2")
    void autoDenyOtherPendingInvitations(UUID studentId, UUID acceptedGroupId);

    @Modifying
    @Query("UPDATE GroupInvitation gi SET gi.status = 'AUTO_DENIED' WHERE gi.status = 'PENDING' AND gi.group.id = ?1")
    void autoDenyAllPendingByGroupId(UUID groupId);
}
