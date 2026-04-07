package com.senior.spm.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.RegisterProfessorRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.service.StaffUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StaffUserService staffUserService;

    public AdminController(StaffUserService staffUserService) {
        this.staffUserService = staffUserService;
    }

    @PostMapping("/register-professor")
    public ResponseEntity<?> registerProfessor(@Valid @RequestBody RegisterProfessorRequest request) {
        var mail = request.getMail();

        try {
            var token = staffUserService.registerProfessor(mail);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("resetToken", token));
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorMessage("Professor with same mail already exists"));
        }
    }
}
