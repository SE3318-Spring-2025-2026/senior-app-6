package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.senior.spm.entity.ScheduleWindow.WindowType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleWindowResponse {

    private UUID id;
    private WindowType type;
    private String termId;
    private LocalDateTime opensAt;
    private LocalDateTime closesAt;
    private boolean isActive;
}
