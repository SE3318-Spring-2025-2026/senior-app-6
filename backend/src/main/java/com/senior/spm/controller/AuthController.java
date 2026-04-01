package com.senior.spm.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.senior.spm.controller.request.LoginRequest;
import com.senior.spm.controller.request.ResetPasswordRequest;
import com.senior.spm.controller.response.LoginResponse;
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.service.JWTService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StaffUserRepository staffUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public AuthController(JWTService jwtService, PasswordEncoder passwordEncoder, StaffUserRepository staffUserRepository,
            PasswordResetTokenRepository passwordResetTokenRepository) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) throws ResponseStatusException {
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

            return new LoginResponse(token, userInfo);
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        var resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        var staffUser = resetToken.getStaff();
        staffUser.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        staffUserRepository.save(staffUser);
        passwordResetTokenRepository.delete(resetToken);
    }
}
