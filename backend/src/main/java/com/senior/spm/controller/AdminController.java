package com.senior.spm.controller;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.RegisterProfessorRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.repository.StaffUserRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PasswordEncoder passwordEncoder;
    private final StaffUserRepository staffUserRepository;

    public AdminController(PasswordEncoder passwordEncoder, StaffUserRepository staffUserRepository) {
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
    }

    @PostMapping("/register-professor")
    public ResponseEntity<ErrorMessage> postMethodName(@Validated @RequestBody RegisterProfessorRequest request) {
        var mail = request.getMail();
        if (staffUserRepository.findByMail(mail).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage("Professor with same mail already exists"));
        }

        var bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        var randomPassword = HexFormat.of().formatHex(bytes);

        //TODO send random password to the professor via something idk
        var staffUser = new StaffUser();
        staffUser.setFirstLogin(true);
        staffUser.setMail(mail);
        var logger = LoggerFactory.getLogger(AdminController.class);
        logger.info(staffUser.toString());
        staffUser.setPasswordHash(passwordEncoder.encode(randomPassword));
        staffUser.setRole(StaffUser.Role.Professor);
        try {
            staffUserRepository.save(staffUser);
        } catch (IllegalArgumentException | OptimisticEntityLockException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorMessage(e.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
