package com.senior.spm.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.senior.spm.controller.request.CreateSprintDeliverableMappingRequest;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintDeliverableMapping;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.SprintDeliverableMappingRepository;
import com.senior.spm.repository.SprintRepository;

@Service
public class SprintService {

    private static final BigDecimal MAX_TOTAL_CONTRIBUTION = new BigDecimal("100");

    private final SprintRepository sprintRepository;
    private final DeliverableRepository deliverableRepository;
    private final SprintDeliverableMappingRepository sprintDeliverableMappingRepository;

    public SprintService(
            SprintRepository sprintRepository,
            DeliverableRepository deliverableRepository,
            SprintDeliverableMappingRepository sprintDeliverableMappingRepository) {
        this.sprintRepository = sprintRepository;
        this.deliverableRepository = deliverableRepository;
        this.sprintDeliverableMappingRepository = sprintDeliverableMappingRepository;
    }

    @Transactional
    public void createDeliverableMapping(UUID sprintId, CreateSprintDeliverableMappingRequest request) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sprint not found with id: " + sprintId));

        Deliverable deliverable = deliverableRepository.findById(request.getDeliverableId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Deliverable not found with id: " + request.getDeliverableId()));

        boolean alreadyExists = sprintDeliverableMappingRepository
                .existsBySprint_IdAndDeliverable_Id(sprintId, request.getDeliverableId());

        if (alreadyExists) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This sprint is already linked to the specified deliverable.");
        }

        BigDecimal assignedContribution = sprintDeliverableMappingRepository
                .sumContributionPercentageByDeliverableId(request.getDeliverableId());

        if (assignedContribution == null) {
            assignedContribution = BigDecimal.ZERO;
        }

        BigDecimal newTotal = assignedContribution.add(request.getContributionPercentage());

        if (newTotal.compareTo(MAX_TOTAL_CONTRIBUTION) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total contribution percentage for this deliverable cannot exceed 100%. Currently assigned: "
                            + assignedContribution.stripTrailingZeros().toPlainString() + "%.");
        }

        SprintDeliverableMapping mapping = new SprintDeliverableMapping();
        mapping.setSprint(sprint);
        mapping.setDeliverable(deliverable);
        mapping.setContributionPercentage(request.getContributionPercentage());

        sprintDeliverableMappingRepository.save(mapping);
    }
}