package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "group_invitation")
@Getter
@Setter
@NoArgsConstructor
public class GroupInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_invitation_group"))
    private ProjectGroup group;

    @ManyToOne
    @JoinColumn(name = "invitee_student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_invitation_student"))
    private Student invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = true)
    private LocalDateTime respondedAt;

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        CANCELLED,
        AUTO_DENIED
    }
}
