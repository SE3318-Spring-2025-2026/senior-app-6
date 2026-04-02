package com.senior.spm.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
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

    public AuthController(JWTService jwtService, PasswordEncoder passwordEncoder, StaffUserRepository staffUserRepository,
            PasswordResetTokenRepository passwordResetTokenRepository, StudentRepository studentRepository) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.studentRepository = studentRepository;
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
        staffUserRepository.save(staffUser);
        passwordResetTokenRepository.delete(resetToken.get());

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

    @PostMapping("/github")
    public ResponseEntity<?> githubLogin(@Valid @RequestBody GithubLoginRequest request) {
        var student = studentRepository.findByStudentId(request.getStudentId())
                .or(() -> studentRepository.findByGithubUsername(request.getUsername()));

        if (student.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorMessage("Student ID not recognized"));
        }

        Student validStudent = student.get();
        var token = jwtService.issueToken(validStudent);

        var userInfo = new GithubLoginResponse.UserInfo(
                validStudent.getId(),
                validStudent.getGithubUsername(),
                "Student");

        return ResponseEntity.status(HttpStatus.OK).body(new GithubLoginResponse(token, userInfo));
    }
}
