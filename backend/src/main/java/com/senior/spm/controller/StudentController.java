package com.senior.spm.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

// TODO: Implement student search endpoint — requires StudentService.searchAvailableStudents(q)
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Validated
public class StudentController {

    /**
     * Search for students not currently in any group.
     * Auth: Student JWT
     * GET /api/students/search?q={query} (min 3 chars)
     * Returns students with studentId, githubUsername, inGroup=false
     */
    @GetMapping("/search")
    public ResponseEntity<List<?>> searchStudents(@RequestParam String q) {
        // TODO: wire to StudentService.searchAvailableStudents(q)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
