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

    public UUID getId()                  { return id; }
    public UUID getUserId()              { return userId; }
    public void setUserId(UUID userId)   { this.userId = userId; }
    public UserType getUserType()        { return userType; }
    public void setUserType(UserType t)  { this.userType = t; }
    public String getAction()            { return action; }
    public void setAction(String action) { this.action = action; }
    public Outcome getOutcome()          { return outcome; }
    public void setOutcome(Outcome o)    { this.outcome = o; }
    public String getIpAddress()         { return ipAddress; }
    public void setIpAddress(String ip)  { this.ipAddress = ip; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime t) { this.occurredAt = t; }
}
