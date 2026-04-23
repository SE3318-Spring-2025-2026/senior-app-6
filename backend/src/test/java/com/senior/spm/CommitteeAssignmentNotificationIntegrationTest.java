package com.senior.spm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.Deliverable.DeliverableType;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.service.CommitteeNotificationGateway;
import com.senior.spm.service.JWTService;
import com.senior.spm.service.dto.CommitteeAssignmentNotificationPayload;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class CommitteeAssignmentNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private CommitteeRepository committeeRepository;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private ProjectGroupRepository projectGroupRepository;

    @MockBean
    private CommitteeNotificationGateway committeeNotificationGateway;

    private StaffUser coordinator;
    private String token;

    private Deliverable deliverable;
    private Committee committee;
    private StaffUser advisorProfessor;
    private StaffUser juryProfessor;

    @BeforeEach
    void setUp() {
        committeeRepository.deleteAll();
        projectGroupRepository.deleteAll();
        staffUserRepository.deleteAll();
        deliverableRepository.deleteAll();

        coordinator = staffUserRepository.save(newCoordinator());
        token = jwtService.issueToken(coordinator);

        advisorProfessor = staffUserRepository.save(newProfessor("advisor@test.com"));
        juryProfessor = staffUserRepository.save(newProfessor("jury@test.com"));

        deliverable = deliverableRepository.save(newDeliverable());

        committee = new Committee();
        committee.setCommitteeName("Committee A");
        committee.setTermId("2026-SPRING");
        committee.setDeliverable(deliverable);
        committee = committeeRepository.save(committee);
    }

    @Test
    void shouldPublishAssignmentNotificationExactlyOnceWhenCommitteeBecomesReady() throws Exception {
        ProjectGroup g1 = projectGroupRepository.save(newGroup("Alpha", GroupStatus.ADVISOR_ASSIGNED));
        ProjectGroup g2 = projectGroupRepository.save(newGroup("Beta", GroupStatus.ADVISOR_ASSIGNED));
        ProjectGroup g3 = projectGroupRepository.save(newGroup("Gamma", GroupStatus.ADVISOR_ASSIGNED));

        String professorBody = objectMapper.writeValueAsString(Map.of(
                "professors", List.of(
                        Map.of("professorId", advisorProfessor.getId(), "role", "ADVISOR"),
                        Map.of("professorId", juryProfessor.getId(), "role", "JURY"))));

        mockMvc.perform(post("/api/committees/{id}/professors", committee.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(professorBody))
                .andExpect(status().isCreated());

        verifyNoInteractions(committeeNotificationGateway);

        String firstGroupBody = objectMapper.writeValueAsString(Map.of(
                "groupIds", List.of(g1.getId(), g2.getId())));

        mockMvc.perform(post("/api/committees/{id}/groups", committee.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstGroupBody))
                .andExpect(status().isCreated());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommitteeAssignmentNotificationPayload>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(committeeNotificationGateway, times(1)).sendAssignmentNotifications(captor.capture());

        List<CommitteeAssignmentNotificationPayload> notifications = captor.getValue();
        assertThat(notifications).hasSize(2);
        assertThat(notifications)
                .extracting(CommitteeAssignmentNotificationPayload::professorMail)
                .containsExactly("advisor@test.com", "jury@test.com");
        assertThat(notifications)
                .extracting(CommitteeAssignmentNotificationPayload::role)
                .extracting(Object::toString)
                .containsExactly("ADVISOR", "JURY");
        assertThat(notifications)
                .allSatisfy(notification -> {
                    assertThat(notification.committeeId()).isEqualTo(committee.getId());
                    assertThat(notification.assignedGroups())
                            .extracting(group -> group.groupName())
                            .containsExactly("Alpha", "Beta");
                });

        Committee persistedCommittee = committeeRepository.findById(committee.getId()).orElseThrow();
        assertThat(persistedCommittee.getAssignmentNotificationSentAt()).isNotNull();

        String secondGroupBody = objectMapper.writeValueAsString(Map.of(
                "groupIds", List.of(g3.getId())));

        mockMvc.perform(post("/api/committees/{id}/groups", committee.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondGroupBody))
                .andExpect(status().isCreated());

        verify(committeeNotificationGateway, times(1)).sendAssignmentNotifications(anyList());
    }

    private StaffUser newCoordinator() {
        StaffUser user = new StaffUser();
        user.setMail("coord@test.com");
        user.setPasswordHash("test");
        user.setRole(Role.Coordinator);
        return user;
    }

    private StaffUser newProfessor(String mail) {
        StaffUser user = new StaffUser();
        user.setMail(mail);
        user.setPasswordHash("test");
        user.setRole(Role.Professor);
        return user;
    }

    private Deliverable newDeliverable() {
        Deliverable d = new Deliverable();
        d.setName("Proposal");
        d.setType(DeliverableType.Proposal);
        d.setSubmissionDeadline(LocalDateTime.now(ZoneId.of("UTC")).plusDays(7));
        d.setReviewDeadline(LocalDateTime.now(ZoneId.of("UTC")).plusDays(14));
        d.setWeight(BigDecimal.valueOf(20));
        return d;
    }

    private ProjectGroup newGroup(String name, GroupStatus status) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setStatus(status);
        g.setTermId("2026-SPRING");
        g.setCreatedAt(LocalDateTime.now());
        g.setVersion(0L);
        return g;
    }
}