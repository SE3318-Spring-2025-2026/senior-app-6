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

    List<GroupInvitation> findByTargetStudentId(UUID targetStudentId);

    List<GroupInvitation> findByTargetStudentIdAndStatus(UUID targetStudentId, InvitationStatus status);

    boolean existsByGroupIdAndTargetStudentIdAndStatus(UUID groupId, UUID targetStudentId, InvitationStatus status);

    @Modifying
    @Query("UPDATE GroupInvitation gi SET gi.status = 'AUTO_DENIED' WHERE gi.status = 'PENDING' AND gi.targetStudent.id = ?1 AND gi.group.id != ?2")
    void autoDenyOtherPendingInvitations(UUID studentId, UUID acceptedGroupId);
}
