package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.UpdateDeliverableWeightRequest;
import com.senior.spm.service.DeliverableService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/coordinator/deliverables")
public class CoordinatorDeliverableController {

    private final DeliverableService deliverableService;

    public CoordinatorDeliverableController(DeliverableService deliverableService) {
        this.deliverableService = deliverableService;
    }

    @PatchMapping("/{id}/weight")
    public ResponseEntity<Void> updateDeliverableWeight(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeliverableWeightRequest request) {

        deliverableService.updateWeight(id, request.getWeightPercentage());
        return ResponseEntity.ok().build();
    }
}