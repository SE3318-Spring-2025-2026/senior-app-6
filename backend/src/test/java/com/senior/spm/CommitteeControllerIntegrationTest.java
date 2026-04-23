package com.senior.spm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.Deliverable.DeliverableType;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.service.JWTService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Note:
 * Current backend implementation validates professor conflicts at the
 * deliverable-assignment level, not by explicit schedule time overlap.
 */


@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class CommitteeControllerIntegrationTest {

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

    private StaffUser coordinator;
    private StaffUser advisor;
    private StaffUser jury1;
    private StaffUser jury2;

    private String coordinatorToken;

    private Deliverable deliverable;
    private Committee committee;

    @BeforeEach
    void setUp() {
        committeeRepository.deleteAll();
        staffUserRepository.deleteAll();
        deliverableRepository.deleteAll();

        coordinator = staffUserRepository.save(newCoordinator("coord@test.com"));
        advisor = staffUserRepository.save(newProfessor("advisor@test.com"));
        jury1 = staffUserRepository.save(newProfessor("jury1@test.com"));
        jury2 = staffUserRepository.save(newProfessor("jury2@test.com"));

        coordinatorToken = jwtService.issueToken(coordinator);

        deliverable = deliverableRepository.save(newDeliverable("Proposal 1"));

        committee = new Committee();
        committee.setCommitteeName("Committee Alpha");
        committee.setTermId("2026-SPRING");
        committee.setDeliverable(deliverable);
        committee = committeeRepository.save(committee);
    }

    @Test
    void shouldFailWhenNoAdvisorSubmitted() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "professors", List.of(
                        Map.of("professorId", jury1.getId(), "role", "JURY"),
                        Map.of("professorId", jury2.getId(), "role", "JURY")
                )
        ));

        mockMvc.perform(post("/api/committees/{id}/professors", committee.getId())
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exactly one ADVISOR is required"));
    }

    @Test
    void shouldFailWhenMultipleAdvisorsSubmitted() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "professors", List.of(
                        Map.of("professorId", advisor.getId(), "role", "ADVISOR"),
                        Map.of("professorId", jury1.getId(), "role", "ADVISOR")
                )
        ));

        mockMvc.perform(post("/api/committees/{id}/professors", committee.getId())
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exactly one ADVISOR is required"));
    }

    @Test
    void shouldFailWhenDuplicateProfessorIdsSubmitted() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "professors", List.of(
                        Map.of("professorId", advisor.getId(), "role", "ADVISOR"),
                        Map.of("professorId", advisor.getId(), "role", "JURY")
                )
        ));

        mockMvc.perform(post("/api/committees/{id}/professors", committee.getId())
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate professor IDs are not allowed"));
    }

    @Test
    void shouldFailWhenProfessorAlreadyAssignedToAnotherCommitteeForSameDeliverable() throws Exception {
        Committee otherCommittee = new Committee();
        otherCommittee.setCommitteeName("Committee Beta");
        otherCommittee.setTermId("2026-SPRING");
        otherCommittee.setDeliverable(deliverable);
        otherCommittee = committeeRepository.save(otherCommittee);

        String firstAssignmentBody = objectMapper.writeValueAsString(Map.of(
                "professors", List.of(
                        Map.of("professorId", advisor.getId(), "role", "ADVISOR"),
                        Map.of("professorId", jury1.getId(), "role", "JURY")
                )
        ));

        mockMvc.perform(post("/api/committees/{id}/professors", otherCommittee.getId())
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstAssignmentBody))
                .andExpect(status().isCreated());

        String conflictingBody = objectMapper.writeValueAsString(Map.of(
                "professors", List.of(
                        Map.of("professorId", advisor.getId(), "role", "ADVISOR"),
                        Map.of("professorId", jury2.getId(), "role", "JURY")
                )
        ));

        mockMvc.perform(post("/api/committees/{id}/professors", committee.getId())
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Professor is already assigned to another committee for this deliverable"));
    }

    @Test
    void shouldPersistValidAssignmentWithOneAdvisorAndMultipleJury() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "professors", List.of(
                        Map.of("professorId", advisor.getId(), "role", "ADVISOR"),
                        Map.of("professorId", jury1.getId(), "role", "JURY"),
                        Map.of("professorId", jury2.getId(), "role", "JURY")
                )
        ));

        mockMvc.perform(post("/api/committees/{id}/professors", committee.getId())
                        .header("Authorization", "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/committees/{id}", committee.getId())
                        .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professors.length()").value(3));

        assertThat(committeeRepository.existsByProfessorIdAndDeliverableId(advisor.getId(), deliverable.getId())).isTrue();
        assertThat(committeeRepository.existsByProfessorIdAndDeliverableId(jury1.getId(), deliverable.getId())).isTrue();
        assertThat(committeeRepository.existsByProfessorIdAndDeliverableId(jury2.getId(), deliverable.getId())).isTrue();
    }

    private StaffUser newCoordinator(String mail) {
        StaffUser user = new StaffUser();
        user.setMail(mail);
        user.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        user.setRole(Role.Coordinator);
        return user;
    }

    private StaffUser newProfessor(String mail) {
        StaffUser user = new StaffUser();
        user.setMail(mail);
        user.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        user.setRole(Role.Professor);
        user.setAdvisorCapacity(5);
        return user;
    }

    private Deliverable newDeliverable(String name) {
        Deliverable d = new Deliverable();
        d.setName(name);
        d.setType(DeliverableType.Proposal);
        d.setSubmissionDeadline(LocalDateTime.now(ZoneId.of("UTC")).plusDays(7));
        d.setReviewDeadline(LocalDateTime.now(ZoneId.of("UTC")).plusDays(14));
        d.setWeight(BigDecimal.valueOf(20.00));
        return d;
    }
}