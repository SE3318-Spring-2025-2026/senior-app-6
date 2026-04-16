package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.CommitteeDetailResponse;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.CommitteeService;

@RestController
@RequestMapping("/api/committees")
public class CommitteeController {

    private final CommitteeService committeeService;

    public CommitteeController(CommitteeService committeeService) {
        this.committeeService = committeeService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCommitteeDetail(@PathVariable UUID id) {
        try {
            CommitteeDetailResponse result = committeeService.getCommitteeDetails(id);
            return ResponseEntity.ok(result);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }
    }
}
