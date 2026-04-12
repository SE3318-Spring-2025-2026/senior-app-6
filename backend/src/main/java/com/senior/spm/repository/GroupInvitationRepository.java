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

    /**
     * Find every invitation associated with a group, regardless of status.
     *
     * @param groupId UUID of the group
     * @return invitations sent by the group
     */
    List<GroupInvitation> findByGroupId(UUID groupId);

    /**
     * Find every invitation received by a student, regardless of status.
     *
     * @param inviteeId internal UUID of the invited student
     * @return invitations received by the student
     */
    List<GroupInvitation> findByInviteeId(UUID inviteeId);

    /**
     * Find invitations received by a student with a specific status.
     *
     * @param inviteeId internal UUID of the invited student
     * @param status invitation status to filter by
     * @return matching invitations
     */
    List<GroupInvitation> findByInviteeIdAndStatus(UUID inviteeId, InvitationStatus status);

    /**
     * Check whether an invitation already exists for a group-student-status tuple.
     *
     * @param groupId UUID of the inviting group
     * @param inviteeId internal UUID of the invited student
     * @param status invitation status to match
     * @return {@code true} when a matching invitation exists
     */
    boolean existsByGroupIdAndInviteeIdAndStatus(UUID groupId, UUID inviteeId, InvitationStatus status);

    /**
     * Count invitations for a group in a specific status.
     *
     * @param groupId UUID of the group
     * @param status invitation status to count
     * @return number of matching invitations
     */
    long countByGroupIdAndStatus(UUID groupId, InvitationStatus status);

    /**
     * Mark a student's other pending invitations as auto-denied after one invitation is accepted.
     *
     * <p>This accept-specific bulk update excludes the accepted invitation by id
     * so the accepted row cannot be overwritten by the bulk query.
     *
     * @param studentId internal UUID of the invited student
     * @param acceptedInvitationId UUID of the accepted invitation that must remain unchanged
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE GroupInvitation gi
           SET gi.status = 'AUTO_DENIED'
         WHERE gi.status = 'PENDING'
           AND gi.invitee.id = ?1
           AND gi.id <> ?2
        """)
    void autoDenyOtherPendingInvitationsExcept(UUID studentId, UUID acceptedInvitationId);

    /**
     * Mark all pending invitations for a student as auto-denied.
     *
     * @param studentId internal UUID of the invited student
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE GroupInvitation gi SET gi.status = 'AUTO_DENIED' WHERE gi.status = 'PENDING' AND gi.invitee.id = ?1")
    void autoDenyAllPendingByInviteeId(UUID studentId);

    /**
     * Mark all pending invitations sent by a group as auto-denied.
     *
     * @param groupId UUID of the group whose pending invitations should be closed
     */
    @Modifying
    @Query("UPDATE GroupInvitation gi SET gi.status = 'AUTO_DENIED' WHERE gi.status = 'PENDING' AND gi.group.id = ?1")
    void autoDenyAllPendingByGroupId(UUID groupId);
}
