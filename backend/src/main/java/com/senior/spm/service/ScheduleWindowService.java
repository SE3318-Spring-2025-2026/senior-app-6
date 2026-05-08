package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.ScheduleWindowRequest;
import com.senior.spm.controller.response.ScheduleWindowResponse;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.ScheduleWindowRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleWindowService {

    private final ScheduleWindowRepository scheduleWindowRepository;
    private final TermConfigService termConfigService;

    public List<ScheduleWindowResponse> getAll() {
        String termId = termConfigService.getActiveTermId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        return Arrays.stream(ScheduleWindow.WindowType.values())
                .map(type -> scheduleWindowRepository.findByTermIdAndType(termId, type)
                        .map(w -> ScheduleWindowResponse.builder()
                                .id(w.getId())
                                .type(w.getType())
                                .termId(w.getTermId())
                                .opensAt(w.getOpensAt())
                                .closesAt(w.getClosesAt())
                                .isActive(!now.isBefore(w.getOpensAt()) && !now.isAfter(w.getClosesAt()))
                                .build())
                        .orElse(ScheduleWindowResponse.builder()
                                .id(null)
                                .type(type)
                                .termId(termId)
                                .opensAt(null)
                                .closesAt(null)
                                .isActive(false)
                                .build()))
                .collect(Collectors.toList());
    }

    /**
     * Upserts a schedule window for the active term.
     * Returns true if a new window was created, false if an existing one was updated.
     */
    @Transactional
    public boolean upsert(ScheduleWindowRequest request) {
        if (!request.getClosesAt().isAfter(request.getOpensAt())) {
            throw new BusinessRuleException("closesAt must be after opensAt");
        }

        String termId = termConfigService.getActiveTermId();
        var existing = scheduleWindowRepository.findByTermIdAndType(termId, request.getType());

        ScheduleWindow window;
        boolean isNew;
        if (existing.isPresent()) {
            window = existing.get();
            isNew = false;
        } else {
            window = new ScheduleWindow();
            window.setTermId(termId);
            window.setType(request.getType());
            isNew = true;
        }

        window.setOpensAt(request.getOpensAt());
        window.setClosesAt(request.getClosesAt());
        scheduleWindowRepository.save(window);
        return isNew;
    }

    public ScheduleWindowResponse getByType(ScheduleWindow.WindowType type) {
        String termId = termConfigService.getActiveTermId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        ScheduleWindow w = scheduleWindowRepository.findByTermIdAndType(termId, type)
                .orElseThrow(() -> new NotFoundException("Schedule window not found"));
        return ScheduleWindowResponse.builder()
                .id(w.getId())
                .type(w.getType())
                .termId(w.getTermId())
                .opensAt(w.getOpensAt())
                .closesAt(w.getClosesAt())
                .isActive(!now.isBefore(w.getOpensAt()) && !now.isAfter(w.getClosesAt()))
                .build();
    }

    public void delete(UUID id) {
        if (!scheduleWindowRepository.existsById(id)) {
            throw new NotFoundException("Schedule window not found");
        }
        scheduleWindowRepository.deleteById(id);
    }
}
