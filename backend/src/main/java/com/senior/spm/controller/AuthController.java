package com.senior.spm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var mail = request.getMail();
        var password = request.getPassword();

        var response = authService.login(mail, password);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorMessage("Invalid credentials"));
        }

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<ErrorMessage> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        var token = request.getToken();
        var newPassword = request.getNewPassword();

        try {
            authService.resetPassword(token, newPassword);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/reset-password")
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        try {
            authService.validateResetToken(token);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("Token is not found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/github")
    public ResponseEntity<?> githubLogin(@Valid @RequestBody GithubLoginRequest request) {
        var studentId = request.getStudentId();
        var githubCode = request.getCode();

        try {
            var response = authService.githubLogin(studentId, githubCode);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage(e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorMessage("Failed to retrieve access token from GitHub"));
        }
    }
}
