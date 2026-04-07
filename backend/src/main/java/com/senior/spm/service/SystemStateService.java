package com.senior.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.SystemState;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemStateRepository;

@Service
public class SystemStateService {

    private final SystemStateRepository systemStateRepository;
    private final StudentRepository studentRepository;
    private final SprintRepository sprintRepository;
    private final DeliverableRepository deliverableRepository;

    public SystemStateService(SystemStateRepository systemStateRepository,
            StudentRepository studentRepository,
            SprintRepository sprintRepository,
            DeliverableRepository deliverableRepository) {
        this.systemStateRepository = systemStateRepository;
        this.studentRepository = studentRepository;
        this.sprintRepository = sprintRepository;
        this.deliverableRepository = deliverableRepository;
    }

    @Transactional
    public void publishSystem() {
        if (studentRepository.count() == 0) {
            throw new IllegalStateException("Incomplete configuration: Missing students");
        }
        if (sprintRepository.count() == 0) {
            throw new IllegalStateException("Incomplete configuration: Missing sprints");
        }
        if (deliverableRepository.count() == 0) {
            throw new IllegalStateException("Incomplete configuration: Missing deliverables");
        }

        SystemState systemState;
        if (systemStateRepository.count() == 0) {
            systemState = new SystemState();
            systemState.setStatus(SystemState.Status.ACTIVE);
        } else {
            systemState = systemStateRepository.findAll().get(0);
            systemState.setStatus(SystemState.Status.ACTIVE);
        }

        systemStateRepository.save(systemState);
    }
}
