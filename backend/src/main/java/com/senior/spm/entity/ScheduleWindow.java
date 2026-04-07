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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedule_window")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String termId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WindowType type;

    @Column(nullable = false)
    private LocalDateTime opensAt;

    @Column(nullable = false)
    private LocalDateTime closesAt;

    public enum WindowType {
        GROUP_CREATION,
        ADVISOR_ASSOCIATION
    }
}
