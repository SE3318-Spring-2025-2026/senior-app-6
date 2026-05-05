package com.senior.spm.service;

import java.time.LocalDateTime;

import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.response.GithubLoginResponse;
import com.senior.spm.controller.response.LoginResponse;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.exception.RepositoryException;
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;

    private final StaffUserRepository staffUserRepository;

    private final JWTService jwtService;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private final StudentRepository studentRepository;

    private final GithubService githubService;

    public AuthService(
            PasswordEncoder passwordEncoder,
            StaffUserRepository staffUserRepository,
            JWTService jwtService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            StudentRepository studentRepository,
            GithubService githubService) {
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.jwtService = jwtService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.studentRepository = studentRepository;
        this.githubService = githubService;
    }

    public LoginResponse login(String mail, String password) {

        var staffUser = staffUserRepository.findByMail(mail);
        if (staffUser.isPresent()
                && passwordEncoder.matches(password, staffUser.get().getPasswordHash())) {
            var token = jwtService.issueToken(staffUser.get());

            var userInfo = new LoginResponse.UserInfo(
                    staffUser.get().getId(),
                    staffUser.get().getMail(),
                    staffUser.get().getRole(),
                    staffUser.get().isFirstLogin());

            log.trace("[EVENT] userId={} action={} entityId={} detail={}",
                    staffUser.get().getId(), "STAFF_LOGIN", staffUser.get().getId(), "staff-password-auth");
            return new LoginResponse(token, userInfo);
        }

        return null;
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        var resetToken = passwordResetTokenRepository.findByToken(token);
        if (resetToken.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        if (resetToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken.get());
            throw new IllegalArgumentException("Password reset token has expired, please request a new one");
        }

        var staffUser = resetToken.get().getStaff();
        staffUser.setPasswordHash(passwordEncoder.encode(newPassword));
        staffUser.setFirstLogin(false);
        try {
            staffUserRepository.save(staffUser);
            passwordResetTokenRepository.delete(resetToken.get());
        } catch (IllegalArgumentException | OptimisticEntityLockException e) {
            throw new RepositoryException("Server error while resetting password: " + e.getMessage(), e);
        }
    }

    public void validateResetToken(String token) {
        var resetToken = passwordResetTokenRepository.findByToken(token);
        if (resetToken.isEmpty()) {
            throw new NotFoundException("Token not found");
        }
        if (resetToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken.get());
            throw new IllegalArgumentException("Token has expired");
        }
    }

    public GithubLoginResponse githubLogin(String studentId, String code) {
        var studentOpt = studentRepository.findByStudentId(studentId);
        if (studentOpt.isEmpty()) {
            throw new NotFoundException("Student ID not found");
        }

        var githubTokenResponse = githubService.exchangeCodeForAccessToken(code);

        if (githubTokenResponse == null || githubTokenResponse.accessToken() == null) {
            throw new RuntimeException("Failed to retrieve access token from GitHub");
        }

        var githubUser = githubService.getGithubUser(githubTokenResponse.accessToken());

        studentOpt.get().setGithubUsername(githubUser.login());
        studentOpt.get().setAccessToken(githubTokenResponse.accessToken());

        studentRepository.save(studentOpt.get());

        var token = jwtService.issueToken(studentOpt.get());

        var userInfo = new GithubLoginResponse.UserInfo(
                studentOpt.get().getId(),
                studentOpt.get().getGithubUsername(),
                "STUDENT");

        log.trace("[EVENT] userId={} action={} entityId={} detail={}",
                studentOpt.get().getId(), "STUDENT_LOGIN", studentOpt.get().getId(), "github-oauth");
        return new GithubLoginResponse(token, userInfo);
    }
}
