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
@Table(name = "advisor_request")
@Getter
@Setter
@NoArgsConstructor
public class AdvisorRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_advisor_request_group"))
    private ProjectGroup group;

    @ManyToOne
    @JoinColumn(name = "advisor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_advisor_request_advisor"))
    private StaffUser advisor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = true)
    private LocalDateTime respondedAt;

    public enum RequestStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        AUTO_REJECTED,
        CANCELLED
    }
}
