package com.senior.spm.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.senior.spm.entity.Deliverable;
import com.senior.spm.repository.DeliverableRepository;

@Service
public class DeliverableService {

    private static final BigDecimal MAX_TOTAL_WEIGHT = new BigDecimal("100");

    private final DeliverableRepository deliverableRepository;

    public DeliverableService(DeliverableRepository deliverableRepository) {
        this.deliverableRepository = deliverableRepository;
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
}