package com.senior.spm.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.entity.FinalGrade;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.FinalGradeRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;

/**
 * Integration tests for {@code GET /api/students/{studentId}/grade} (Issue #286).
 * Covers the 8 scenarios from the spec: auth matrix (coordinator / advisor / non-advisor / self / other),
 * 204 when not yet calculated, 404 when student missing, 400 when format invalid.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class StudentGradeControllerTest {

    private static final String TERM_ID = "2026-SPRING";
    private static final String STUDENT_ID = "12345678901";
    private static final String OTHER_STUDENT_ID = "98765432109";
    private static final String UNKNOWN_STUDENT_ID = "99999999999";

    @Autowired private MockMvc mockMvc;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private GroupMembershipRepository groupMembershipRepository;
    @Autowired private FinalGradeRepository finalGradeRepository;

    private Student student;
    private StaffUser advisor;
    private StaffUser otherProfessor;
    private StaffUser coordinator;
    private ProjectGroup group;

    @BeforeEach
    void setUp() {
        cleanAll();

        SystemConfig sc = new SystemConfig();
        sc.setConfigKey("active_term_id");
        sc.setConfigValue(TERM_ID);
        systemConfigRepository.save(sc);

        advisor         = staffUserRepository.save(staff("advisor@test.com", StaffUser.Role.Professor));
        otherProfessor  = staffUserRepository.save(staff("other@test.com",   StaffUser.Role.Professor));
        coordinator     = staffUserRepository.save(staff("coord@test.com",   StaffUser.Role.Coordinator));

        Student s = new Student();
        s.setStudentId(STUDENT_ID);
        student = studentRepository.save(s);

        Student o = new Student();
        o.setStudentId(OTHER_STUDENT_ID);
        studentRepository.save(o);

        ProjectGroup g = new ProjectGroup();
        g.setGroupName("TeamAlpha");
        g.setTermId(TERM_ID);
        g.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        g.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        g.setAdvisor(advisor);
        group = projectGroupRepository.save(g);

        GroupMembership gm = new GroupMembership();
        gm.setGroup(group);
        gm.setStudent(student);
        gm.setRole(GroupMembership.MemberRole.MEMBER);
        gm.setJoinedAt(LocalDateTime.now());
        groupMembershipRepository.save(gm);

        FinalGrade fg = new FinalGrade();
        fg.setStudent(student);
        fg.setGroup(group);
        fg.setTermId(TERM_ID);
        fg.setWeightedTotal(new BigDecimal("75.5000"));
        fg.setCompletionRatio(new BigDecimal("0.9000"));
        fg.setFinalGrade(new BigDecimal("67.9500"));
        fg.setCalculatedAt(LocalDateTime.now());
        finalGradeRepository.save(fg);
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        finalGradeRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        staffUserRepository.deleteAll();
        studentRepository.deleteAll();
        systemConfigRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /api/students/{studentId}/grade — auth matrix + content
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Coordinator: 200 with stored grade fields and deliverableBreakdown=[]")
    void storedGrade_coordinator_returns200WithEmptyBreakdown() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", STUDENT_ID)
                        .with(authentication(coordinatorAuth(coordinator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID))
                .andExpect(jsonPath("$.groupId").value(group.getId().toString()))
                .andExpect(jsonPath("$.deliverableBreakdown").isArray())
                .andExpect(jsonPath("$.deliverableBreakdown").isEmpty())
                .andExpect(jsonPath("$.weightedTotal").value(75.5))
                .andExpect(jsonPath("$.completionRatio").value(0.9))
                .andExpect(jsonPath("$.finalGrade").value(67.95))
                .andExpect(jsonPath("$.calculatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Professor (advisor of student's group): 200")
    void storedGrade_advisorProfessor_returns200() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", STUDENT_ID)
                        .with(authentication(professorAuth(advisor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID));
    }

    @Test
    @DisplayName("Professor not advisor of student's group: 403")
    void storedGrade_nonAdvisorProfessor_returns403() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", STUDENT_ID)
                        .with(authentication(professorAuth(otherProfessor))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student requesting own grade: 200")
    void storedGrade_studentSelf_returns200() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", STUDENT_ID)
                        .with(authentication(studentAuth(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_ID));
    }

    @Test
    @DisplayName("Student requesting another student's grade: 403")
    void storedGrade_studentOther_returns403() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", OTHER_STUDENT_ID)
                        .with(authentication(studentAuth(student))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No grade row for student in active term: 204 No Content")
    void storedGrade_noRecord_returns204() throws Exception {
        finalGradeRepository.deleteAll();

        mockMvc.perform(get("/api/students/{studentId}/grade", STUDENT_ID)
                        .with(authentication(coordinatorAuth(coordinator))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Student does not exist (professor caller resolves target): 404")
    void storedGrade_studentNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", UNKNOWN_STUDENT_ID)
                        .with(authentication(professorAuth(advisor))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Coordinator with unknown studentId: 404 (not 204) — student is resolved before role check")
    void storedGrade_coordinatorUnknownStudent_returns404() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", UNKNOWN_STUDENT_ID)
                        .with(authentication(coordinatorAuth(coordinator))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("studentId not 11 digits: 400")
    void storedGrade_invalidStudentIdFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/grade", "abc")
                        .with(authentication(coordinatorAuth(coordinator))))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private StaffUser staff(String mail, StaffUser.Role role) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAdvisorCapacity(5);
        return u;
    }

    private Authentication coordinatorAuth(StaffUser c) {
        return new UsernamePasswordAuthenticationToken(
                c.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));
    }

    private Authentication professorAuth(StaffUser p) {
        return new UsernamePasswordAuthenticationToken(
                p.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR")));
    }

    private Authentication studentAuth(Student s) {
        return new UsernamePasswordAuthenticationToken(
                s.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }
}
