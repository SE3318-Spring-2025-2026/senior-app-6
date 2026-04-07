package com.senior.spm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.Sprint;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.SprintRepository;

@Service
public class SprintService {

    private final SprintRepository sprintRepository;

    public SprintService(SprintRepository sprintRepository) {
        this.sprintRepository = sprintRepository;
    }

    @Transactional
    public Sprint createSprint(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        Sprint sprint = new Sprint();
        sprint.setStartDate(startDate);
        sprint.setEndDate(endDate);

        return sprintRepository.save(sprint);
    }

    @Transactional
    public void updateSprintTarget(UUID id, int storyPointTarget) {
        var sprint = sprintRepository.findById(id);
        if (sprint.isEmpty()) {
            throw new NotFoundException("Sprint not found with ID: " + id);
        }

        sprint.get().setStoryPointTarget(storyPointTarget);
        sprintRepository.save(sprint.get());
    }

    public List<Sprint> getAllSprints() {
        return sprintRepository.findAll();
    }
}
