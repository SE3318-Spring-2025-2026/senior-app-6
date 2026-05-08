package com.senior.spm.controller.request;

import java.time.LocalDateTime;

import com.senior.spm.entity.ScheduleWindow.WindowType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScheduleWindowRequest {

    @NotNull
    private WindowType type;

    @NotNull
    private LocalDateTime opensAt;

    @NotNull
    private LocalDateTime closesAt;
}
