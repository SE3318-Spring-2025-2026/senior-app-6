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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditLog {

    public enum UserType { STAFF, STUDENT }
    public enum Outcome  { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = true)
    private UserType userType;

    @Column(length = 100, nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Outcome outcome;

    @Column(length = 45, nullable = true)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
