package com.senior.spm.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.FinalGradeResponse;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.service.FinalGradeCalculationService;

/**
 * Controller for Process 7.2 — Final Grade calculation endpoint.
 *
 * <p>REST Endpoint: {@code GET /api/students/{studentId}/grade/calculate}
 * <p>{@code studentId} is the <b>11-digit student number</b> (pattern {@code ^[0-9]{11}$}),
 * <b>not</b> a UUID.
 *
 * <p><b>NOTE — GET with side effects:</b> This endpoint computes the grade on-the-fly
 * and upserts the result to the {@code FinalGrade} table before returning. This is a
 * documented side effect, consistent with {@code phase1_2.md} Step 11 ("kaydedilir")
 * and the OpenAPI contract (see endpoints_p7.md D3).
 *
 * <p><b>Auth rules (enforced in-method, not via SecurityConfig role guard):</b>
 * <ul>
 *   <li>{@code ROLE_COORDINATOR} — always allowed.</li>
 *   <li>{@code ROLE_PROFESSOR} — allowed only if the caller is the advisor of the
 *       student's group ({@code group.advisorId == callerUUID}).</li>
 *   <li>{@code ROLE_STUDENT} — allowed only if the student resolved from the JWT
 *       {@code id} UUID has {@code studentId} matching the path param.</li>
 * </ul>
 *
 * <p>SecurityConfig exposes this path to all authenticated users
 * ({@code .anyRequest().authenticated()}); fine-grained auth is done here.
 */
@RestController
@RequestMapping("/api/students")
public class GradingController {

    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^[0-9]{11}$");

    private final FinalGradeCalculationService finalGradeCalculationService;
    private final StudentRepository studentRepository;
    private final GroupMembershipRepository groupMembershipRepository;

    public GradingController(FinalGradeCalculationService finalGradeCalculationService,
                             StudentRepository studentRepository,
                             GroupMembershipRepository groupMembershipRepository) {
        this.finalGradeCalculationService = finalGradeCalculationService;
        this.studentRepository = studentRepository;
        this.groupMembershipRepository = groupMembershipRepository;
    }

    /**
     * Calculates (and upserts) the final grade for the student with the given 11-digit student number.
     *
     * @param studentId 11-digit student number — resolved via {@code StudentRepository.findByStudentId()}
     * @return 200 with {@link FinalGradeResponse}; 403 if caller is unauthorized; 404 if student not found
     */
    @GetMapping("/{studentId}/grade/calculate")
    public ResponseEntity<?> calculateGrade(@PathVariable String studentId) {

        // Validate path parameter format
        if (!STUDENT_ID_PATTERN.matcher(studentId).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorMessage("studentId must be an 11-digit number"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
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
        String callerUuidStr = (String) auth.getPrincipal();
        UUID callerUUID;
        try {
            callerUUID = UUID.fromString(callerUuidStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorMessage("Invalid token principal"));
        }

        // Coordinator — always allowed
        if (isCoordinator) {
            return doCalculate(studentId);
        }

        // Resolve the target student for auth checks
        Student targetStudent;
        try {
            targetStudent = studentRepository.findByStudentId(studentId)
                    .orElseThrow(() -> new NotFoundException("Student not found"));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage(e.getMessage()));
        }

        if (isProfessor) {
            // Professor — allowed only if caller is the advisor of the student's group
            GroupMembership membership = groupMembershipRepository.findByStudentId(targetStudent.getId())
                    .orElse(null);
            if (membership == null || membership.getGroup().getAdvisor() == null
                    || !membership.getGroup().getAdvisor().getId().equals(callerUUID)) {
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
