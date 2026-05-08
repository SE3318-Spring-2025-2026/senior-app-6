package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "audit_log",
    indexes = {
        @Index(name = "idx_al_user_id",     columnList = "userId"),
        @Index(name = "idx_al_outcome",     columnList = "outcome"),
        @Index(name = "idx_al_occurred_at", columnList = "occurredAt")
    }
)
public class AuditLog {

    public enum UserType { STAFF, STUDENT }
    public enum Outcome  { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserType userType;

    @Column(length = 100, nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Outcome outcome;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
