package com.senior.spm;

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
import com.senior.spm.service.JWTService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class CommitteeGroupAssignmentIntegrationTest {

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

    private StaffUser coordinator;
    private String token;

    private Deliverable deliverable;
    private Committee committee;

    @BeforeEach
    void setUp() {
        committeeRepository.deleteAll();
        projectGroupRepository.deleteAll();
        staffUserRepository.deleteAll();
        deliverableRepository.deleteAll();

        coordinator = staffUserRepository.save(newCoordinator());
        token = jwtService.issueToken(coordinator);

        deliverable = deliverableRepository.save(newDeliverable());

        committee = new Committee();
        committee.setCommitteeName("Committee A");
        committee.setTermId("2026-SPRING");
        committee.setDeliverable(deliverable);
        committee = committeeRepository.save(committee);
    }

    @AfterEach
    void tearDown() {
        // committee_group join rows are removed when Committee rows are deleted (owning side of @ManyToMany)
        committeeRepository.deleteAll();
        projectGroupRepository.deleteAll();
        staffUserRepository.deleteAll();
        deliverableRepository.deleteAll();
    }

    @Test
    void shouldFailWhenGroupDoesNotHaveAdvisorAssignedStatus() throws Exception {
        ProjectGroup group = projectGroupRepository.save(
                newGroup("Group 1", GroupStatus.FORMING)
        );

        String body = objectMapper.writeValueAsString(
                Map.of("groupIds", List.of(group.getId()))
        );

        mockMvc.perform(post("/api/committees/{id}/groups", committee.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailWhenGroupAlreadyAssignedToAnotherCommitteeForSameDeliverable() throws Exception {
        ProjectGroup group = projectGroupRepository.save(
                newGroup("Group 2", GroupStatus.ADVISOR_ASSIGNED)
        );

        Committee other = new Committee();
        other.setCommitteeName("Committee B");
        other.setTermId("2026-SPRING");
        other.setDeliverable(deliverable);
        other = committeeRepository.save(other);

        String firstBody = objectMapper.writeValueAsString(
                Map.of("groupIds", List.of(group.getId()))
        );

        mockMvc.perform(post("/api/committees/{id}/groups", other.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isCreated());

        String secondBody = objectMapper.writeValueAsString(
                Map.of("groupIds", List.of(group.getId()))
        );

        mockMvc.perform(post("/api/committees/{id}/groups", committee.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPersistValidBatchGroupAssignment() throws Exception {
        ProjectGroup g1 = projectGroupRepository.save(
                newGroup("Alpha", GroupStatus.ADVISOR_ASSIGNED)
        );

        ProjectGroup g2 = projectGroupRepository.save(
                newGroup("Beta", GroupStatus.ADVISOR_ASSIGNED)
        );

        String body = objectMapper.writeValueAsString(
                Map.of("groupIds", List.of(g1.getId(), g2.getId()))
        );

        mockMvc.perform(post("/api/committees/{id}/groups", committee.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/committees/{id}", committee.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(2));

        assertThat(committeeRepository.existsByGroupIdAndDeliverableId(g1.getId(), deliverable.getId())).isTrue();
        assertThat(committeeRepository.existsByGroupIdAndDeliverableId(g2.getId(), deliverable.getId())).isTrue();
    }

    private StaffUser newCoordinator() {
        StaffUser user = new StaffUser();
        user.setMail("coord@test.com");
        user.setPasswordHash("test");
        user.setRole(Role.Coordinator);
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