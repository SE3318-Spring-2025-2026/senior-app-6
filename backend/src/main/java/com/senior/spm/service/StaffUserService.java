package com.senior.spm.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.senior.spm.entity.PasswordResetToken;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.RepositoryException;
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.StaffUserRepository;

import jakarta.transaction.Transactional;

@Service
public class StaffUserService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffUserService(PasswordEncoder passwordEncoder, StaffUserRepository staffUserRepository, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    public String registerProfessor(String mail, Integer capacity)
            throws RepositoryException, AlreadyExistsException {
        if (staffUserRepository.findByMail(mail).isPresent()) {
            throw new AlreadyExistsException("A user with the provided email already exists.");
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
        staffUser.setAdvisorCapacity(capacity != null ? capacity : 5);
        staffUser.setFirstLogin(true);
        staffUser.setMail(mail);
        staffUser.setPasswordHash(passwordEncoder.encode(randomPassword));
        staffUser.setRole(StaffUser.Role.Professor);
        try {
            staffUserRepository.save(staffUser);
            resetToken.setStaff(staffUser);
            passwordResetTokenRepository.save(resetToken);
        } catch (IllegalArgumentException | OptimisticEntityLockException e) {
            throw new RepositoryException("Server error while creating professor: " + e.getMessage(), e);
        }

        return token;
    }
}
