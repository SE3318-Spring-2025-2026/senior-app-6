package com.senior.spm.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;

/**
 * Integration tests for schedule window CRUD endpoints and AdvisorService window guard.
 * Issue #245 — Deliverable 1.
 *
 * Endpoints: GET/POST/DELETE /api/coordinator/schedule-windows
 *            POST /api/groups/{groupId}/advisor-request (window-check side-effect)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class ScheduleWindowControllerTest {

    private static final String TERM_ID = "2026-SPRING-SW-TEST";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private ScheduleWindowRepository scheduleWindowRepository;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private GroupMembershipRepository groupMembershipRepository;
    @Autowired private AdvisorRequestRepository advisorRequestRepository;

    private StaffUser coordinator;
    private StaffUser professor;

    @BeforeEach
    void setUp() {
        cleanAll();

        SystemConfig sc = new SystemConfig();
        sc.setConfigKey("active_term_id");
        sc.setConfigValue(TERM_ID);
        systemConfigRepository.save(sc);

        coordinator = staffUserRepository.save(makeStaff("coord-sw@test.com", StaffUser.Role.Coordinator));
        professor   = staffUserRepository.save(makeStaff("prof-sw@test.com",  StaffUser.Role.Professor));
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        advisorRequestRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        scheduleWindowRepository.deleteAll();
        staffUserRepository.deleteAll();
        systemConfigRepository.deleteAll();
    }

    // ── GET /api/coordinator/schedule-windows ────────────────────────────────

    @Test
    @DisplayName("GET with no windows → 2 entries, both isActive:false, id:null")
    void getAll_noWindowsConfigured_returnsTwoPlaceholders() throws Exception {
        mockMvc.perform(get("/api/coordinator/schedule-windows")
                        .with(authentication(coordinatorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].isActive").value(false))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[1].isActive").value(false))
                .andExpect(jsonPath("$[1].id").doesNotExist());
    }

    // ── POST /api/coordinator/schedule-windows ────────────────────────────────

    @Test
    @DisplayName("POST GROUP_CREATION window → 201 Created")
    void post_newWindow_returns201() throws Exception {
        mockMvc.perform(post("/api/coordinator/schedule-windows")
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(windowBody("GROUP_CREATION", 1, 8)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Second POST same type → 200 OK (upsert, no duplicate)")
    void post_sameTypeTwice_returns200OnSecond() throws Exception {
        mockMvc.perform(post("/api/coordinator/schedule-windows")
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(windowBody("GROUP_CREATION", 1, 8)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/coordinator/schedule-windows")
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(windowBody("GROUP_CREATION", 2, 9)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST with closesAt <= opensAt → 400")
    void post_closesAtBeforeOpensAt_returns400() throws Exception {
        LocalDateTime base = LocalDateTime.now(ZoneId.of("UTC")).plusDays(1);
        Map<String, Object> body = Map.of(
                "type", "GROUP_CREATION",
                "opensAt", base.toString(),
                "closesAt", base.minusHours(1).toString()  // closes BEFORE opens
        );
        mockMvc.perform(post("/api/coordinator/schedule-windows")
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Non-coordinator POST → 403 Forbidden")
    void post_nonCoordinator_returns403() throws Exception {
        mockMvc.perform(post("/api/coordinator/schedule-windows")
                        .with(authentication(professorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(windowBody("GROUP_CREATION", 1, 8)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/coordinator/schedule-windows/{id} ─────────────────────────

    @Test
    @DisplayName("DELETE existing window → 204 No Content")
    void delete_existingWindow_returns204() throws Exception {
        ScheduleWindow w = saveWindow(ScheduleWindow.WindowType.GROUP_CREATION, 1, 8);

        mockMvc.perform(delete("/api/coordinator/schedule-windows/{id}", w.getId())
                        .with(authentication(coordinatorAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE same window twice → second returns 404")
    void delete_nonExistentId_returns404() throws Exception {
        ScheduleWindow w = saveWindow(ScheduleWindow.WindowType.ADVISOR_ASSOCIATION, 1, 8);
        UUID id = w.getId();

        mockMvc.perform(delete("/api/coordinator/schedule-windows/{id}", id)
                        .with(authentication(coordinatorAuth())))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/coordinator/schedule-windows/{id}", id)
                        .with(authentication(coordinatorAuth())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Non-coordinator DELETE → 403 Forbidden")
    void delete_nonCoordinator_returns403() throws Exception {
        ScheduleWindow w = saveWindow(ScheduleWindow.WindowType.GROUP_CREATION, 1, 8);

        mockMvc.perform(delete("/api/coordinator/schedule-windows/{id}", w.getId())
                        .with(authentication(professorAuth())))
                .andExpect(status().isForbidden());
    }

    // ── Window check fires before opensAt ─────────────────────────────────────

    @Test
    @DisplayName("AdvisorService: ADVISOR_ASSOCIATION window exists but not yet open → 400")
    void advisorRequest_windowNotYetOpen_returns400() throws Exception {
        // Window starts tomorrow → not yet active
        saveWindow(ScheduleWindow.WindowType.ADVISOR_ASSOCIATION, 1, 8);

        // Group in TOOLS_BOUND with matching termId
        ProjectGroup group = new ProjectGroup();
        group.setGroupName("WinTestGroup");
        group.setTermId(TERM_ID);
        group.setStatus(ProjectGroup.GroupStatus.TOOLS_BOUND);
        group.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        group.setVersion(0L);
        group = projectGroupRepository.save(group);

        Student student = new Student();
        student.setStudentId("10000000001");
        student = studentRepository.save(student);

        GroupMembership gm = new GroupMembership();
        gm.setGroup(group);
        gm.setStudent(student);
        gm.setRole(GroupMembership.MemberRole.TEAM_LEADER);
        gm.setJoinedAt(LocalDateTime.now());
        groupMembershipRepository.save(gm);

        Map<String, Object> body = Map.of("advisorId", professor.getId().toString());

        mockMvc.perform(post("/api/groups/{groupId}/advisor-request", group.getId())
                        .with(authentication(studentAuth(student)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StaffUser makeStaff(String mail, StaffUser.Role role) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAdvisorCapacity(5);
        return u;
    }

    private ScheduleWindow saveWindow(ScheduleWindow.WindowType type, int openDaysFromNow, int closeDaysFromNow) {
        ScheduleWindow w = new ScheduleWindow();
        w.setTermId(TERM_ID);
        w.setType(type);
        w.setOpensAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(openDaysFromNow));
        w.setClosesAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(closeDaysFromNow));
        return scheduleWindowRepository.save(w);
    }

    /** Builds a JSON body with opensAt+N days and closesAt+M days from now. */
    private String windowBody(String type, int openDays, int closeDays) throws Exception {
        LocalDateTime base = LocalDateTime.now(ZoneId.of("UTC"));
        Map<String, Object> body = Map.of(
                "type", type,
                "opensAt", base.plusDays(openDays).toString(),
                "closesAt", base.plusDays(closeDays).toString()
        );
        return objectMapper.writeValueAsString(body);
    }

    private Authentication coordinatorAuth() {
        return new UsernamePasswordAuthenticationToken(
                coordinator.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));
    }

    private Authentication professorAuth() {
        return new UsernamePasswordAuthenticationToken(
                professor.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR")));
    }

    private Authentication studentAuth(Student s) {
        return new UsernamePasswordAuthenticationToken(
                s.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }
}
