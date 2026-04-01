package com.senior.spm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.senior.spm.controller.request.GithubLoginRequest;
import com.senior.spm.controller.request.LoginRequest;
import com.senior.spm.controller.response.GithubLoginResponse;
import com.senior.spm.controller.response.LoginResponse;
import com.senior.spm.entity.Student;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.service.JWTService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StaffUserRepository staffUserRepository;
    private final StudentRepository studentRepository;

    public AuthController(JWTService jwtService, PasswordEncoder passwordEncoder, StaffUserRepository staffUserRepository,
            StudentRepository studentRepository) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.studentRepository = studentRepository;
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

    @PostMapping("/github")
    public GithubLoginResponse githubLogin(@Valid @RequestBody GithubLoginRequest request) {
        var student = studentRepository.findByStudentId(request.getStudentId())
                .or(() -> studentRepository.findByGithubUsername(request.getUsername()));

        if (student.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Student ID not recognized");
        }

        Student validStudent = student.get();
        var token = jwtService.issueToken(validStudent);

        var userInfo = new GithubLoginResponse.UserInfo(
                validStudent.getId(),
                validStudent.getGithubUsername(),
                "Student");

        return new GithubLoginResponse(token, userInfo);
    }
}
