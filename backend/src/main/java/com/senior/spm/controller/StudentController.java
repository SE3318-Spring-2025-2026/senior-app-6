package com.senior.spm.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.StudentSearchResponse;
import com.senior.spm.service.StudentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Validated
public class StudentController {

    private final StudentService studentService;

    /**
     * Search for students not currently in any group.
     * Auth: Student JWT
     * GET /api/students/search?q={query} (min 3 chars)
     * Returns students with studentId, githubUsername (null if not yet OAuth'd)
     */
    @GetMapping("/search")
    public ResponseEntity<List<StudentSearchResponse>> searchStudents(@RequestParam String q) {
        return ResponseEntity.ok(studentService.searchAvailableStudents(q));
    }
}
