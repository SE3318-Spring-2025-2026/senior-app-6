package com.senior.spm.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.entity.AuditLog;
import com.senior.spm.entity.AuditLog.Category;
import com.senior.spm.entity.AuditLog.Outcome;
import com.senior.spm.entity.AuditLog.UserType;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.repository.AuditLogRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.service.JWTService;

/**
 * Integration tests for GET /api/admin/audit-logs (issue #302).
 *
 * Covers:
 *   – Auth matrix: coordinator → 200, admin → 200, professor → 403, student → 403, no token → 401
 *   – size=500 → capped to 100 in response
 *   – ?outcome=FAILURE filter returns only FAILURE rows
 *   – ?userId=not-a-uuid → 400
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditLogControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JWTService jwtService;

    @Autowired AuditLogRepository auditLogRepository;
    @Autowired StaffUserRepository staffUserRepository;
    @Autowired StudentRepository studentRepository;

    private String coordinatorToken;
    private String adminToken;
    private String professorToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        cleanAll();

        StaffUser coordinator = staffUserRepository.save(makeStaff("coord@al-test.com", Role.Coordinator));
        StaffUser admin       = staffUserRepository.save(makeStaff("admin@al-test.com", Role.Admin));
        StaffUser professor   = staffUserRepository.save(makeStaff("prof@al-test.com",  Role.Professor));
        Student   student     = studentRepository.save(makeStudent("44400000001"));

        coordinatorToken = jwtService.issueToken(coordinator);
        adminToken       = jwtService.issueToken(admin);
        professorToken   = jwtService.issueToken(professor);
        studentToken     = jwtService.issueToken(student);
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Auth matrix
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Coordinator GET /audit-logs → 200")
    void coordinator_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin GET /audit-logs → 200")
    void admin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Professor GET /audit-logs → 403")
    void professor_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student GET /audit-logs → 403")
    void student_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No token GET /audit-logs → 401")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Page size cap
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("size=500 in request → size=100 in response (cap enforced)")
    void pageSizeCap_500becomes100() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + coordinatorToken)
                .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Outcome filter
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("?outcome=FAILURE returns only FAILURE rows")
    void outcomeFilter_returnsOnlyFailureRows() throws Exception {
        auditLogRepository.save(makeAuditLog("STAFF_LOGIN", Category.AUTH, Outcome.SUCCESS));
        auditLogRepository.save(makeAuditLog("STAFF_LOGIN", Category.AUTH, Outcome.FAILURE));
        auditLogRepository.save(makeAuditLog("STAFF_LOGIN", Category.AUTH, Outcome.FAILURE));

        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + coordinatorToken)
                .param("outcome", "FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].outcome", everyItem(is("FAILURE"))));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Invalid UUID → 400
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("?userId=not-a-uuid → 400")
    void invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", "Bearer " + coordinatorToken)
                .param("userId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private void cleanAll() {
        auditLogRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
    }

    private StaffUser makeStaff(String mail, Role role) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        u.setRole(role);
        u.setAdvisorCapacity(5);
        return u;
    }

    private Student makeStudent(String studentId) {
        Student s = new Student();
        s.setStudentId(studentId);
        return s;
    }

    private AuditLog makeAuditLog(String action, Category category, Outcome outcome) {
        AuditLog log = new AuditLog();
        log.setUserType(UserType.STAFF);
        log.setAction(action);
        log.setCategory(category);
        log.setOutcome(outcome);
        log.setOccurredAt(LocalDateTime.now(ZoneId.of("UTC")));
        return log;
    }
}
