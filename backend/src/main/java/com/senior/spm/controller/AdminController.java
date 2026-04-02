package com.senior.spm.controller;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.RegisterProfessorRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.entity.PasswordResetToken;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.StaffUserRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffUserRepository staffUserRepository;

    public AdminController(PasswordEncoder passwordEncoder, StaffUserRepository staffUserRepository, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @PostMapping("/register-professor")
    @Transactional
    public ResponseEntity<?> registerProfessor(@Validated @RequestBody RegisterProfessorRequest request) {
        var mail = request.getMail();
        if (staffUserRepository.findByMail(mail).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage("Professor with same mail already exists"));
        }

        var bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        var randomPassword = HexFormat.of().formatHex(bytes);

        var token = UUID.randomUUID().toString();
        var resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setCreatedAt(LocalDateTime.now());
        resetToken.setExpiresAt(LocalDateTime.now().plusDays(1));

        var staffUser = new StaffUser();
        staffUser.setFirstLogin(true);
        staffUser.setMail(mail);
        staffUser.setPasswordHash(passwordEncoder.encode(randomPassword));
        staffUser.setRole(StaffUser.Role.Professor);
        try {
            staffUserRepository.save(staffUser);
            resetToken.setStaff(staffUser);
            passwordResetTokenRepository.save(resetToken);
        } catch (IllegalArgumentException | OptimisticEntityLockException e) {
            throw new RuntimeException("Server error while creating professor: " + e.getMessage(), e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("resetToken", token));
    }
}
