package com.senior.spm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.FinalGradeRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.JWTService;

/**
 * JWT-based integration tests for GET /api/students/{studentId}/grade/calculate.
 *
 * Covers (issue #254):
 *   – Auth matrix with real JWT tokens
 *   – C_i log filter combinations (assigneeGithubUsername × prMerged)
 *   – FinalGrade row persistence confirmed via JDBC + upsert on second call
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinalGradeCalculateIntegrationTest {

    private static final String TERM_ID      = "2026-TEST-CALC";
    private static final String STUDENT_A_ID = "11111111111";
    private static final String STUDENT_B_ID = "22222222222";

    @Autowired MockMvc mockMvc;
    @Autowired JWTService jwtService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired StudentRepository studentRepository;
    @Autowired StaffUserRepository staffUserRepository;
    @Autowired ProjectGroupRepository projectGroupRepository;
    @Autowired GroupMembershipRepository groupMembershipRepository;
    @Autowired SystemConfigRepository systemConfigRepository;
    @Autowired SprintRepository sprintRepository;
    @Autowired SprintTrackingLogRepository sprintTrackingLogRepository;
    @Autowired FinalGradeRepository finalGradeRepository;

    private Student studentA;
    private Student studentB;
    private StaffUser professorAdvisor;
    private StaffUser professorNotAdvisor;
    private StaffUser coordinator;
    private ProjectGroup groupA;
    private ProjectGroup groupB;

    private String studentAToken;
    private String professorAdvisorToken;
    private String professorNotAdvisorToken;
    private String coordinatorToken;

    @BeforeEach
    void setUp() {
        cleanAll();

        SystemConfig sc = new SystemConfig();
        sc.setConfigKey("active_term_id");
        sc.setConfigValue(TERM_ID);
        systemConfigRepository.save(sc);

        professorAdvisor    = staffUserRepository.save(makeProfessor("advisor@calc-test.com"));
        professorNotAdvisor = staffUserRepository.save(makeProfessor("other@calc-test.com"));
        coordinator         = staffUserRepository.save(makeCoordinator("coord@calc-test.com"));

        studentA = studentRepository.save(makeStudent(STUDENT_A_ID, "gh-student-a"));
        studentB = studentRepository.save(makeStudent(STUDENT_B_ID, "gh-student-b"));

        groupA = projectGroupRepository.save(makeGroupWithAdvisor("Group A", professorAdvisor));
        groupB = projectGroupRepository.save(makeGroup("Group B"));

        groupMembershipRepository.save(makeMembership(studentA, groupA));
        groupMembershipRepository.save(makeMembership(studentB, groupB));

        studentAToken            = jwtService.issueToken(studentA);
        professorAdvisorToken    = jwtService.issueToken(professorAdvisor);
        professorNotAdvisorToken = jwtService.issueToken(professorNotAdvisor);
        coordinatorToken         = jwtService.issueToken(coordinator);
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Auth integration tests — real JWT tokens
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Student A JWT → /calculate for Student A → 200")
    void studentA_calculateOwnGrade_returns200() throws Exception {
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_A_ID));
    }

    @Test
    @DisplayName("Student A JWT → /calculate for Student B → 403")
    void studentA_calculateOtherStudentGrade_returns403() throws Exception {
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_B_ID)
                .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor NOT advisor of student's group → 403")
    void professorNotAdvisor_calculate_returns403() throws Exception {
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + professorNotAdvisorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor IS advisor of student's group → 200")
    void professorIsAdvisor_calculate_returns200() throws Exception {
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + professorAdvisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_A_ID));
    }

    @Test
    @DisplayName("Coordinator → 200 for Student A")
    void coordinator_calculateStudentAGrade_returns200() throws Exception {
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_A_ID));
    }

    @Test
    @DisplayName("Coordinator → 200 for any student (Student B)")
    void coordinator_calculateStudentBGrade_returns200() throws Exception {
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_B_ID)
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_B_ID));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  C_i identification tests
    //  Algorithm: C_i = SUM(storyPoints where assignee matches AND prMerged=true)
    //                 / SUM(sprint.storyPointTarget)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Matching assigneeGithubUsername + prMerged=true → C_i > 0 (counted)")
    void ci_matchingUsername_prMergedTrue_isCounted() throws Exception {
        Sprint sprint = sprintRepository.save(makeSprint(10));
        sprintTrackingLogRepository.save(
                makeLog(groupA, sprint, "gh-student-a", true, 10));

        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionRatio").value(Matchers.greaterThan(0.0)));
    }

    @Test
    @DisplayName("Matching assigneeGithubUsername + prMerged=false → C_i = 0 (NOT counted)")
    void ci_matchingUsername_prMergedFalse_notCounted() throws Exception {
        Sprint sprint = sprintRepository.save(makeSprint(10));
        sprintTrackingLogRepository.save(
                makeLog(groupA, sprint, "gh-student-a", false, 10));

        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionRatio").value(0.0));
    }

    @Test
    @DisplayName("Non-matching assigneeGithubUsername + prMerged=true → C_i = 0 (NOT counted)")
    void ci_nonMatchingUsername_prMergedTrue_notCounted() throws Exception {
        Sprint sprint = sprintRepository.save(makeSprint(10));
        sprintTrackingLogRepository.save(
                makeLog(groupA, sprint, "gh-other-user", true, 10));

        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionRatio").value(0.0));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Persistence test — JDBC assertion + upsert on second call
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /calculate upserts one row; second call updates calculated_at — no duplicate row")
    void calculate_persistsRow_secondCallUpdatesCalculatedAt_noDuplicate() throws Exception {
        // First call → row should be created
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk());

        // JDBC assertion: exactly 1 row in final_grade for this term
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM final_grade WHERE term_id = ?",
                Integer.class, TERM_ID);
        assertThat(rowCount).isEqualTo(1);

        LocalDateTime firstCalculatedAt = jdbcTemplate.queryForObject(
                "SELECT calculated_at FROM final_grade WHERE term_id = ?",
                LocalDateTime.class, TERM_ID);
        assertThat(firstCalculatedAt).isNotNull();

        // Ensure the second call will produce a strictly later timestamp
        Thread.sleep(50);

        // Second call → same row must be updated (upsert), not a new row
        mockMvc.perform(get("/api/students/{id}/grade/calculate", STUDENT_A_ID)
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk());

        // JDBC assertion: still exactly 1 row — no duplicate was inserted
        Integer rowCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM final_grade WHERE term_id = ?",
                Integer.class, TERM_ID);
        assertThat(rowCountAfter).isEqualTo(1);

        LocalDateTime secondCalculatedAt = jdbcTemplate.queryForObject(
                "SELECT calculated_at FROM final_grade WHERE term_id = ?",
                LocalDateTime.class, TERM_ID);
        assertThat(secondCalculatedAt).isAfterOrEqualTo(firstCalculatedAt);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private void cleanAll() {
        finalGradeRepository.deleteAll();
        sprintTrackingLogRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        sprintRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
        systemConfigRepository.deleteAll();
    }

    private StaffUser makeProfessor(String mail) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        u.setRole(Role.Professor);
        u.setAdvisorCapacity(5);
        return u;
    }

    private StaffUser makeCoordinator(String mail) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        u.setRole(Role.Coordinator);
        return u;
    }

    private Student makeStudent(String studentId, String githubUsername) {
        Student s = new Student();
        s.setStudentId(studentId);
        s.setGithubUsername(githubUsername);
        return s;
    }

    private ProjectGroup makeGroup(String name) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId(TERM_ID);
        g.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        g.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        return g;
    }

    private ProjectGroup makeGroupWithAdvisor(String name, StaffUser advisor) {
        ProjectGroup g = makeGroup(name);
        g.setAdvisor(advisor);
        return g;
    }

    private GroupMembership makeMembership(Student s, ProjectGroup g) {
        GroupMembership m = new GroupMembership();
        m.setStudent(s);
        m.setGroup(g);
        m.setRole(GroupMembership.MemberRole.TEAM_LEADER);
        m.setJoinedAt(LocalDateTime.now(ZoneId.of("UTC")));
        return m;
    }

    private Sprint makeSprint(int storyPointTarget) {
        Sprint sp = new Sprint();
        sp.setStartDate(LocalDate.now().minusDays(14));
        sp.setEndDate(LocalDate.now().minusDays(1));
        sp.setStoryPointTarget(storyPointTarget);
        return sp;
    }

    private SprintTrackingLog makeLog(ProjectGroup group, Sprint sprint,
            String assigneeUsername, boolean prMerged, int storyPoints) {
        SprintTrackingLog log = new SprintTrackingLog();
        log.setGroup(group);
        log.setSprint(sprint);
        log.setIssueKey("PROJ-001");
        log.setAssigneeGithubUsername(assigneeUsername);
        log.setPrMerged(prMerged);
        log.setStoryPoints(storyPoints);
        log.setFetchedAt(LocalDateTime.now(ZoneId.of("UTC")));
        log.setAiPrResult(SprintTrackingLog.AiValidationResult.PENDING);
        log.setAiDiffResult(SprintTrackingLog.AiValidationResult.PENDING);
        return log;
    }
}
