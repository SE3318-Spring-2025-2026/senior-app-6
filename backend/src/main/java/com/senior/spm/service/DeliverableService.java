package com.senior.spm.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.AddRubricRequest;
import com.senior.spm.controller.request.CreateDeliverableRequest;
import com.senior.spm.controller.request.MapDeliverablesRequest;
import com.senior.spm.controller.request.UpdateDeliverableRequest;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.SprintDeliverableMapping;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.SprintDeliverableMappingRepository;
import com.senior.spm.repository.SprintRepository;

@Service
public class DeliverableService {

    private final DeliverableRepository deliverableRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final SprintRepository sprintRepository;
    private final SprintDeliverableMappingRepository sprintDeliverableMappingRepository;

    public DeliverableService(DeliverableRepository deliverableRepository,
            RubricCriterionRepository rubricCriterionRepository,
            SprintRepository sprintRepository,
            SprintDeliverableMappingRepository sprintDeliverableMappingRepository) {
        this.deliverableRepository = deliverableRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
        this.sprintRepository = sprintRepository;
        this.sprintDeliverableMappingRepository = sprintDeliverableMappingRepository;
    }

    @Transactional
    public Deliverable createDeliverable(CreateDeliverableRequest request) {
        if (request.getReviewDeadline().isBefore(request.getSubmissionDeadline())) {
            throw new IllegalArgumentException("Review deadline cannot be before submission deadline");
        }

        Deliverable deliverable = new Deliverable();
        deliverable.setName(request.getName());
        deliverable.setType(request.getType());
        deliverable.setSubmissionDeadline(request.getSubmissionDeadline());
        deliverable.setReviewDeadline(request.getReviewDeadline());

        return deliverableRepository.save(deliverable);
    }

    @Transactional
    public Deliverable updateDeliverableWeight(UUID id, BigDecimal weightPercentage) {
        var deliverable = deliverableRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Deliverable not found with ID: " + id));

        deliverable.setWeight(weightPercentage);
        return deliverableRepository.save(deliverable);
    }

    @Transactional
    public RubricCriterion addRubricToDeliverable(UUID id, AddRubricRequest request) {
        var deliverable = deliverableRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Deliverable not found with ID: " + id));

        var existingCriteria = rubricCriterionRepository.findAllByDeliverableId(id);

        var existingTotalWeight = existingCriteria.stream()
                .map(RubricCriterion::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var newTotalWeight = existingTotalWeight.add(request.getWeight());

        if (newTotalWeight.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Total weight percentage exceeds 100%. Remaining capacity: "
                    + (new BigDecimal("100.00").subtract(existingTotalWeight)) + "%");
        }

        var rubricCriterion = new RubricCriterion();
        rubricCriterion.setDeliverable(deliverable);
        rubricCriterion.setCriterionName(request.getCriterionName());
        rubricCriterion.setGradingType(request.getGradingType());
        rubricCriterion.setWeight(request.getWeight());

        return rubricCriterionRepository.save(rubricCriterion);
    }

    @Transactional
    public void mapDeliverablesToSprint(UUID id, MapDeliverablesRequest request) {
        var sprint = sprintRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sprint not found with ID: " + id));

        var deliverable = deliverableRepository.findById(request.getDeliverableId())
                .orElseThrow(() -> new NotFoundException("Deliverable not found with ID: " + request.getDeliverableId()));

        var existingMappingOpt = sprintDeliverableMappingRepository.findBySprintIdAndDeliverableId(id, request.getDeliverableId());
        var existingMappings = sprintDeliverableMappingRepository.findAllByDeliverableId(request.getDeliverableId());

        BigDecimal otherMappingsTotal = existingMappings.stream()
                .filter(m -> !m.getSprint().getId().equals(id))
                .map(SprintDeliverableMapping::getContributionPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = otherMappingsTotal.add(request.getContributionPercentage());

        if (newTotal.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Total contribution exceeds 100%. Remaining capacity: "
                    + (new BigDecimal("100.00").subtract(otherMappingsTotal)) + "%");
        }

        SprintDeliverableMapping mappingToSave = existingMappingOpt.orElseGet(() -> {
            var newMapping = new SprintDeliverableMapping();
            newMapping.setSprint(sprint);
            newMapping.setDeliverable(deliverable);
            return newMapping;
        });

        mappingToSave.setContributionPercentage(request.getContributionPercentage());
        sprintDeliverableMappingRepository.save(mappingToSave);
    }

    @Transactional
    public Deliverable updateDeliverable(UUID id, UpdateDeliverableRequest request) {
        var existingDeliverable = deliverableRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Deliverable not found with ID: " + id));

        if (request.getName() != null) {
            existingDeliverable.setName(request.getName());
        }
        if (request.getType() != null) {
            existingDeliverable.setType(request.getType());
        }
        if (request.getSubmissionDeadline() != null) {
            existingDeliverable.setSubmissionDeadline(request.getSubmissionDeadline());
        }
        if (request.getReviewDeadline() != null) {
            existingDeliverable.setReviewDeadline(request.getReviewDeadline());
        }
        if (request.getWeight() != null) {
            existingDeliverable.setWeight(request.getWeight());
        }

        return deliverableRepository.save(existingDeliverable);
    }

    public List<Deliverable> getAllDeliverables() {
        return deliverableRepository.findAll();
    }
}
