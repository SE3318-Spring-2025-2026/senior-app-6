package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.FinalGradeResponse;
import com.senior.spm.controller.response.StudentSearchResponse;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.service.FinalGradeCalculationService;
import com.senior.spm.service.GroupService;
import com.senior.spm.service.StudentService;
import com.senior.spm.util.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Validated
public class StudentController {

    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^[0-9]{11}$");

    private final StudentService studentService;
    private final FinalGradeCalculationService finalGradeCalculationService;
    private final GroupService groupService;

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

    /**
     * Calculates (and upserts) the final grade for the student with the given 11-digit student number.
     *
     * @param studentId 11-digit student number — resolved via {@code StudentRepository.findByStudentId()}
     * @return 200 with {@link FinalGradeResponse}; 403 if caller is unauthorized; 404 if student not found
     */
    @GetMapping("/{studentId}/grade/calculate")
    public ResponseEntity<?> calculateGrade(@PathVariable String studentId, Authentication auth) {

        // Validate path parameter format
        if (!STUDENT_ID_PATTERN.matcher(studentId).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorMessage("studentId must be an 11-digit number"));
        }

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorMessage("Unauthorized"));
        }

        // Caller's role comes from the JWT filter: "ROLE_COORDINATOR", "ROLE_PROFESSOR", "ROLE_STUDENT"
        boolean isCoordinator = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINATOR"));
        boolean isProfessor = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PROFESSOR"));
        boolean isStudent = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));

        // Caller UUID is stored as principal (set by JwtAuthenticationFilter)
        UUID callerUUID = SecurityUtils.extractPrincipalUUID(auth);

        // Coordinator — always allowed
        if (isCoordinator) {
            return doCalculate(studentId);
        }

        // Resolve the target student for auth checks
        com.senior.spm.entity.Student targetStudent;
        try {
            targetStudent = studentService.getStudentByStudentId(studentId);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }

        if (isProfessor) {
            // Professor — allowed only if caller is the advisor of the student's group
            UUID advisorId = groupService.getAdvisorIdForStudent(targetStudent.getId());
            if (advisorId == null || !advisorId.equals(callerUUID)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorMessage("Caller is unauthorized to view this grade"));
            }
            return doCalculate(studentId);
        }

        if (isStudent) {
            // Student — allowed only if caller's UUID matches the target student's UUID
            if (!callerUUID.equals(targetStudent.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorMessage("Caller is unauthorized to view this grade"));
            }
            return doCalculate(studentId);
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorMessage("Caller is unauthorized to view this grade"));
    }

    private ResponseEntity<?> doCalculate(String studentId) {
        try {
            FinalGradeResponse response = finalGradeCalculationService.calculateFinalGrade(studentId);
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorMessage("An unexpected error occurred: " + e.getMessage()));
        }
    }
}
