package com.senior.spm.controller;

import java.time.LocalDateTime;

import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.GithubLoginRequest;
import com.senior.spm.controller.request.LoginRequest;
import com.senior.spm.controller.request.ResetPasswordRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.GithubLoginResponse;
import com.senior.spm.controller.response.LoginResponse;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.RepositoryException;
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.service.GithubService;
import com.senior.spm.service.JWTService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StaffUserRepository staffUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final StudentRepository studentRepository;
    private final GithubService githubService;

    public AuthController(JWTService jwtService, PasswordEncoder passwordEncoder, StaffUserRepository staffUserRepository,
            PasswordResetTokenRepository passwordResetTokenRepository, StudentRepository studentRepository,
            GithubService githubService) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.studentRepository = studentRepository;
        this.githubService = githubService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var mail = request.getMail();

        var staffUser = staffUserRepository.findByMail(mail);
        if (staffUser.isPresent()
                && passwordEncoder.matches(request.getPassword(), staffUser.get().getPasswordHash())) {
            var token = jwtService.issueToken(staffUser.get());

            var userInfo = new LoginResponse.UserInfo(
                    staffUser.get().getId(),
                    staffUser.get().getMail(),
                    staffUser.get().getRole(),
                    staffUser.get().isFirstLogin());

            return ResponseEntity.status(HttpStatus.OK).body(new LoginResponse(token, userInfo));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorMessage("Invalid credentials"));
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<ErrorMessage> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        var resetToken = passwordResetTokenRepository.findByToken(request.getToken());
        if (resetToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("Invalid or expired token"));
        }

        if (resetToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken.get());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorMessage("Password reset token has expired, please request a new one"));
        }

        var staffUser = resetToken.get().getStaff();
        staffUser.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        staffUser.setFirstLogin(false);
        try {
            staffUserRepository.save(staffUser);
            passwordResetTokenRepository.delete(resetToken.get());
        } catch (IllegalArgumentException | OptimisticEntityLockException e) {
            throw new RepositoryException("Server error while resetting password: " + e.getMessage(), e);
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/reset-password")
    public ResponseEntity<ErrorMessage> validateResetToken(@RequestParam String token) {
        var resetToken = passwordResetTokenRepository.findByToken(token);
        if (resetToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Token is not found"));
        }
        if (resetToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken.get());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("Token has expired"));
        }
        var staffUser = resetToken.get().getStaff();

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    private record GithubTokenRequest(
            String client_id,
            String client_secret,
            String code) {

    }

    private record GithubTokenResponse(
            String access_token,
            String token_type,
            String scope) {

    }

    @PostMapping("/github")
    public ResponseEntity<?> githubLogin(@Valid @RequestBody GithubLoginRequest request) {
        var studentOpt = studentRepository.findByStudentId(request.getStudentId());
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Student ID not found"));
        }

        var githubTokenResponse = githubService.exchangeCodeForAccessToken(request.getCode());

        if (githubTokenResponse == null || githubTokenResponse.accessToken() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorMessage("Failed to retrieve access token from GitHub"));
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

        return ResponseEntity.status(HttpStatus.OK).body(new GithubLoginResponse(token, userInfo));
    }
}
