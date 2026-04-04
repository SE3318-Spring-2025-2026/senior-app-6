package com.senior.spm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.senior.spm.controller.request.CreateRubricCriterionRequest;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.RubricCriterionRepository;

@Service
public class DeliverableService {

    private static final BigDecimal MAX_TOTAL_WEIGHT = new BigDecimal("100");

    private final DeliverableRepository deliverableRepository;
    private final RubricCriterionRepository rubricCriterionRepository;

    public DeliverableService(
            DeliverableRepository deliverableRepository,
            RubricCriterionRepository rubricCriterionRepository) {
        this.deliverableRepository = deliverableRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
    }

    @Transactional
    public void updateWeight(UUID deliverableId, BigDecimal newWeight) {
        if (newWeight == null || newWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Weight percentage must be greater than 0.");
        }

        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Deliverable not found with id: " + deliverableId));

        BigDecimal assignedWeight = deliverableRepository.sumWeightsExcludingDeliverableInTerm(
                deliverable.getTermId(),
                deliverableId);

        if (assignedWeight == null) {
            assignedWeight = BigDecimal.ZERO;
        }

        BigDecimal newTotal = assignedWeight.add(newWeight);

        if (newTotal.compareTo(MAX_TOTAL_WEIGHT) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total deliverable weight cannot exceed 100%. Currently assigned: "
                            + assignedWeight.stripTrailingZeros().toPlainString() + "%.");
        }

        deliverable.setWeight(newWeight);
        deliverableRepository.save(deliverable);
    }

    @Transactional
    public void createRubric(UUID deliverableId, List<CreateRubricCriterionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one rubric criterion is required.");
        }

        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Deliverable not found with id: " + deliverableId));

        List<RubricCriterion> rubricCriteria = new ArrayList<>();

        for (CreateRubricCriterionRequest request : requests) {
            if (request == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each rubric criterion must be a valid object.");
            }

            RubricCriterion.GradingType gradingType;
            try {
                gradingType = RubricCriterion.GradingType.valueOf(request.getGradingType());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "gradingType must be either 'Binary' or 'Soft'.");
            }

            RubricCriterion rubricCriterion = new RubricCriterion();
            rubricCriterion.setDeliverable(deliverable);
            rubricCriterion.setCriterionName(request.getCriterionName().trim());
            rubricCriterion.setGradingType(gradingType);
            rubricCriterion.setWeight(request.getWeight());

            rubricCriteria.add(rubricCriterion);
        }

        rubricCriterionRepository.saveAll(rubricCriteria);
    }
}