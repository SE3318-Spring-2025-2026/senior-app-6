package com.senior.spm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.AdvisorRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class P3AdvisorLifecycleIntegrationTest {

    private static final String TERM_ID = "2026-SPRING";

    private final AtomicInteger studentSequence = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private AdvisorRequestRepository advisorRequestRepository;
    @Autowired private GroupMembershipRepository groupMembershipRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private ScheduleWindowRepository scheduleWindowRepository;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;

    @BeforeEach
    void resetDatabase() {
        advisorRequestRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        scheduleWindowRepository.deleteAll();
        staffUserRepository.deleteAll();
        studentRepository.deleteAll();
        systemConfigRepository.deleteAll();

        SystemConfig activeTerm = new SystemConfig();
        activeTerm.setConfigKey("active_term_id");
        activeTerm.setConfigValue(TERM_ID);
        systemConfigRepository.save(activeTerm);
    }

    @AfterEach
    void cleanUp() {
        resetDatabase();
    }

    @Test
    @DisplayName("send request returns 201 and cancel returns 200 with PENDING/CANCELLED DB states")
    void sendRequestThenCancel_updatesDatabaseLifecycleStates() throws Exception {
        openAdvisorWindow();
        StaffUser advisor = professor("advisor-lifecycle@example.edu", 5);
        Student leader = student();
        ProjectGroup group = group("Lifecycle Team", ProjectGroup.GroupStatus.TOOLS_BOUND);
        membership(group, leader, GroupMembership.MemberRole.TEAM_LEADER);

        MvcResult sendResult = mockMvc.perform(post("/api/groups/{groupId}/advisor-request", group.getId())
                .with(authentication(studentAuth(leader)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(advisorRequestBody(advisor.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        UUID requestId = UUID.fromString(json(sendResult).get("requestId").asText());
        assertThat(advisorRequestRepository.findById(requestId))
                .get()
                .extracting(AdvisorRequest::getStatus)
                .isEqualTo(AdvisorRequest.RequestStatus.PENDING);

        mockMvc.perform(delete("/api/groups/{groupId}/advisor-request", group.getId())
                .with(authentication(studentAuth(leader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(advisorRequestRepository.findById(requestId))
                .get()
                .extracting(AdvisorRequest::getStatus)
                .isEqualTo(AdvisorRequest.RequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("send request rejects non-TOOLS_BOUND group with 400")
    void sendRequest_groupNotToolsBound_returns400() throws Exception {
        openAdvisorWindow();
        StaffUser advisor = professor("advisor-status@example.edu", 5);
        Student leader = student();
        ProjectGroup group = group("Not Ready Team", ProjectGroup.GroupStatus.FORMING);
        membership(group, leader, GroupMembership.MemberRole.TEAM_LEADER);

        mockMvc.perform(post("/api/groups/{groupId}/advisor-request", group.getId())
                .with(authentication(studentAuth(leader)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(advisorRequestBody(advisor.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("send request rejects duplicate pending request with 409")
    void sendRequest_duplicatePendingRequest_returns409() throws Exception {
        openAdvisorWindow();
        StaffUser advisor = professor("advisor-duplicate@example.edu", 5);
        Student leader = student();
        ProjectGroup group = group("Duplicate Team", ProjectGroup.GroupStatus.TOOLS_BOUND);
        membership(group, leader, GroupMembership.MemberRole.TEAM_LEADER);
        advisorRequest(group, advisor, AdvisorRequest.RequestStatus.PENDING);

        mockMvc.perform(post("/api/groups/{groupId}/advisor-request", group.getId())
                .with(authentication(studentAuth(leader)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(advisorRequestBody(advisor.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("send request rejects advisor at capacity with 400")
    void sendRequest_advisorAtCapacity_returns400() throws Exception {
        openAdvisorWindow();
        StaffUser advisor = professor("advisor-full@example.edu", 1);
        Student leader = student();
        ProjectGroup group = group("Capacity Team", ProjectGroup.GroupStatus.TOOLS_BOUND);
        membership(group, leader, GroupMembership.MemberRole.TEAM_LEADER);
        assignedGroup("Already Assigned Team", advisor);

        mockMvc.perform(post("/api/groups/{groupId}/advisor-request", group.getId())
                .with(authentication(studentAuth(leader)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(advisorRequestBody(advisor.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("send request rejects group member who is not team leader with 403")
    void sendRequest_nonTeamLeader_returns403() throws Exception {
        openAdvisorWindow();
        StaffUser advisor = professor("advisor-member@example.edu", 5);
        Student member = student();
        ProjectGroup group = group("Member Team", ProjectGroup.GroupStatus.TOOLS_BOUND);
        membership(group, member, GroupMembership.MemberRole.MEMBER);

        mockMvc.perform(post("/api/groups/{groupId}/advisor-request", group.getId())
                .with(authentication(studentAuth(member)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(advisorRequestBody(advisor.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("professor can reject pending request and DB remains TOOLS_BOUND")
    void professorRejectsPendingRequest_updatesOnlyRequestState() throws Exception {
        StaffUser advisor = professor("advisor-reject@example.edu", 5);
        ProjectGroup group = group("Reject Team", ProjectGroup.GroupStatus.TOOLS_BOUND);
        AdvisorRequest request = advisorRequest(group, advisor, AdvisorRequest.RequestStatus.PENDING);

        mockMvc.perform(patch("/api/advisor/requests/{requestId}/respond", request.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(advisorRequestRepository.findById(request.getId()))
                .get()
                .extracting(AdvisorRequest::getStatus)
                .isEqualTo(AdvisorRequest.RequestStatus.REJECTED);
        assertThat(projectGroupRepository.findById(group.getId()))
                .get()
                .extracting(ProjectGroup::getStatus)
                .isEqualTo(ProjectGroup.GroupStatus.TOOLS_BOUND);
    }

    @Test
    @DisplayName("coordinator force-assign bypasses closed window and capacity, then auto-rejects pending requests")
    void coordinatorForceAssign_atCapacityAndClosedWindow_returns200AndCascadesAutoRejected() throws Exception {
        closedAdvisorWindow();
        StaffUser fullAdvisor = professor("advisor-force-full@example.edu", 1);
        StaffUser pendingAdvisorOne = professor("advisor-pending-1@example.edu", 5);
        StaffUser pendingAdvisorTwo = professor("advisor-pending-2@example.edu", 5);
        assignedGroup("Advisor Existing Team", fullAdvisor);
        ProjectGroup targetGroup = group("Override Team", ProjectGroup.GroupStatus.TOOLS_BOUND);
        AdvisorRequest requestOne = advisorRequest(targetGroup, pendingAdvisorOne, AdvisorRequest.RequestStatus.PENDING);
        AdvisorRequest requestTwo = advisorRequest(targetGroup, pendingAdvisorTwo, AdvisorRequest.RequestStatus.PENDING);

        mockMvc.perform(patch("/api/coordinator/groups/{groupId}/advisor", targetGroup.getId())
                .with(authentication(coordinatorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("ASSIGN", fullAdvisor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADVISOR_ASSIGNED"))
                .andExpect(jsonPath("$.advisorId").value(fullAdvisor.getId().toString()));

        ProjectGroup reloadedGroup = projectGroupRepository.findById(targetGroup.getId()).orElseThrow();
        assertThat(reloadedGroup.getStatus()).isEqualTo(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        assertThat(reloadedGroup.getAdvisor().getId()).isEqualTo(fullAdvisor.getId());
        assertThat(advisorRequestRepository.findById(requestOne.getId()))
                .get()
                .extracting(AdvisorRequest::getStatus)
                .isEqualTo(AdvisorRequest.RequestStatus.AUTO_REJECTED);
        assertThat(advisorRequestRepository.findById(requestTwo.getId()))
                .get()
                .extracting(AdvisorRequest::getStatus)
                .isEqualTo(AdvisorRequest.RequestStatus.AUTO_REJECTED);
    }

    @Test
    @DisplayName("coordinator remove advisor returns 200 and reverts group to TOOLS_BOUND")
    void coordinatorRemoveAdvisor_returns200AndRevertsGroupToToolsBound() throws Exception {
        StaffUser advisor = professor("advisor-remove@example.edu", 5);
        ProjectGroup group = assignedGroup("Remove Advisor Team", advisor);

        mockMvc.perform(patch("/api/coordinator/groups/{groupId}/advisor", group.getId())
                .with(authentication(coordinatorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("REMOVE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TOOLS_BOUND"))
                .andExpect(jsonPath("$.advisorId").doesNotExist());

        ProjectGroup reloadedGroup = projectGroupRepository.findById(group.getId()).orElseThrow();
        assertThat(reloadedGroup.getStatus()).isEqualTo(ProjectGroup.GroupStatus.TOOLS_BOUND);
        assertThat(reloadedGroup.getAdvisor()).isNull();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String advisorRequestBody(UUID advisorId) {
        return "{\"advisorId\":\"" + advisorId + "\"}";
    }

    private String overrideBody(String action, UUID advisorId) {
        if (advisorId == null) {
            return "{\"action\":\"" + action + "\"}";
        }
        return "{\"action\":\"" + action + "\",\"advisorId\":\"" + advisorId + "\"}";
    }

    private void openAdvisorWindow() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        scheduleWindow(now.minusDays(1), now.plusDays(1));
    }

    private void closedAdvisorWindow() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        scheduleWindow(now.minusDays(3), now.minusDays(1));
    }

    private void scheduleWindow(LocalDateTime opensAt, LocalDateTime closesAt) {
        ScheduleWindow window = new ScheduleWindow();
        window.setTermId(TERM_ID);
        window.setType(ScheduleWindow.WindowType.ADVISOR_ASSOCIATION);
        window.setOpensAt(opensAt);
        window.setClosesAt(closesAt);
        scheduleWindowRepository.save(window);
    }

    private StaffUser professor(String mail, int capacity) {
        StaffUser user = new StaffUser();
        user.setMail(mail);
        user.setPasswordHash("hash");
        user.setRole(StaffUser.Role.Professor);
        user.setAdvisorCapacity(capacity);
        return staffUserRepository.save(user);
    }

    private Student student() {
        Student student = new Student();
        student.setStudentId(String.format("22%09d", studentSequence.getAndIncrement()));
        return studentRepository.save(student);
    }

    private ProjectGroup group(String name, ProjectGroup.GroupStatus status) {
        ProjectGroup group = new ProjectGroup();
        group.setGroupName(name);
        group.setTermId(TERM_ID);
        group.setStatus(status);
        group.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")).minusHours(1));
        return projectGroupRepository.save(group);
    }

    private ProjectGroup assignedGroup(String name, StaffUser advisor) {
        ProjectGroup group = group(name, ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        group.setAdvisor(advisor);
        return projectGroupRepository.save(group);
    }

    private GroupMembership membership(ProjectGroup group, Student student, GroupMembership.MemberRole role) {
        GroupMembership membership = new GroupMembership();
        membership.setGroup(group);
        membership.setStudent(student);
        membership.setRole(role);
        membership.setJoinedAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(30));
        return groupMembershipRepository.save(membership);
    }

    private AdvisorRequest advisorRequest(ProjectGroup group, StaffUser advisor, AdvisorRequest.RequestStatus status) {
        AdvisorRequest request = new AdvisorRequest();
        request.setGroup(group);
        request.setAdvisor(advisor);
        request.setStatus(status);
        request.setSentAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(10));
        return advisorRequestRepository.save(request);
    }

    private Authentication studentAuth(Student student) {
        return auth(student.getId(), "ROLE_STUDENT");
    }

    private Authentication professorAuth(StaffUser professor) {
        return auth(professor.getId(), "ROLE_PROFESSOR");
    }

    private Authentication coordinatorAuth() {
        return auth(UUID.randomUUID(), "ROLE_COORDINATOR");
    }

    private Authentication auth(UUID principalId, String role) {
        return new UsernamePasswordAuthenticationToken(
                principalId.toString(),
                null,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
