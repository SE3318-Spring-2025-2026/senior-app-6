package com.senior.spm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.Deliverable.DeliverableType;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.repository.CommitteeProfessorRepository;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.service.JWTService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class ProfessorCommitteeDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private CommitteeRepository committeeRepository;

    @Autowired
    private CommitteeProfessorRepository committeeProfessorRepository;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private ProjectGroupRepository projectGroupRepository;

    @Autowired
    private RubricCriterionRepository rubricCriterionRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    private StaffUser professor;
    private StaffUser otherProfessor;
    private StaffUser coordinator;

    private String professorToken;
    private String coordinatorToken;

    @BeforeEach
    void setUp() {
        committeeProfessorRepository.deleteAll();
        committeeRepository.deleteAll();
        rubricCriterionRepository.deleteAll();
        projectGroupRepository.deleteAll();
        staffUserRepository.deleteAll();
        deliverableRepository.deleteAll();

        professor = staffUserRepository.save(newStaff("prof@test.com", Role.Professor));
        otherProfessor = staffUserRepository.save(newStaff("other-prof@test.com", Role.Professor));
        coordinator = staffUserRepository.save(newStaff("coord@test.com", Role.Coordinator));

        professorToken = jwtService.issueToken(professor);
        coordinatorToken = jwtService.issueToken(coordinator);
    }

    @Test
    void shouldReturnOnlyAuthenticatedProfessorsCommitteesWithGroupsAndRubrics() throws Exception {
        Deliverable proposal = deliverableRepository.save(newDeliverable("Proposal", DeliverableType.Proposal));
        Deliverable demo = deliverableRepository.save(newDeliverable("Demo", DeliverableType.Demonstration));

        rubricCriterionRepository.save(newRubric(proposal, "Technical Quality", "40.00"));
        rubricCriterionRepository.save(newRubric(proposal, "Presentation", "60.00"));
        rubricCriterionRepository.save(newRubric(demo, "Demo Flow", "100.00"));

        Committee mine = new Committee();
        mine.setCommitteeName("Committee A");
        mine.setTermId("2026-SPRING");
        mine.setDeliverable(proposal);
        mine = committeeRepository.save(mine);

        ProjectGroup alpha = projectGroupRepository.save(newGroup("Alpha"));
        ProjectGroup beta = projectGroupRepository.save(newGroup("Beta"));

        mine.getGroups().add(alpha);
        mine.getGroups().add(beta);
        mine = committeeRepository.save(mine);

        CommitteeProfessor myAssignment = new CommitteeProfessor();
        myAssignment.setCommittee(mine);
        myAssignment.setProfessor(professor);
        myAssignment.setRole(CommitteeProfessor.ProfessorRole.ADVISOR);
        committeeProfessorRepository.save(myAssignment);

        Committee hidden = new Committee();
        hidden.setCommitteeName("Committee B");
        hidden.setTermId("2026-SPRING");
        hidden.setDeliverable(demo);
        hidden = committeeRepository.save(hidden);

        ProjectGroup gamma = projectGroupRepository.save(newGroup("Gamma"));
        hidden.getGroups().add(gamma);
        hidden = committeeRepository.save(hidden);

        CommitteeProfessor otherAssignment = new CommitteeProfessor();
        otherAssignment.setCommittee(hidden);
        otherAssignment.setProfessor(otherProfessor);
        otherAssignment.setRole(CommitteeProfessor.ProfessorRole.JURY);
        committeeProfessorRepository.save(otherAssignment);

        mockMvc.perform(get("/api/professors/me/committees")
                        .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].committeeName").value("Committee A"))
                .andExpect(jsonPath("$[0].professorRole").value("ADVISOR"))
                .andExpect(jsonPath("$[0].deliverableName").value("Proposal"))
                .andExpect(jsonPath("$[0].groups.length()").value(2))
                .andExpect(jsonPath("$[0].groups[0].groupName").value("Alpha"))
                .andExpect(jsonPath("$[0].groups[1].groupName").value("Beta"))
                .andExpect(jsonPath("$[0].rubrics.length()").value(2));
    }

    @Test
    void coordinatorShouldNotAccessProfessorDashboardEndpoint() throws Exception {
        mockMvc.perform(get("/api/professors/me/committees")
                        .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isForbidden());
    }

    private StaffUser newStaff(String mail, Role role) {
        StaffUser user = new StaffUser();
        user.setMail(mail);
        user.setPasswordHash("test");
        user.setRole(role);
        return user;
    }

    private Deliverable newDeliverable(String name, DeliverableType type) {
        Deliverable deliverable = new Deliverable();
        deliverable.setName(name);
        deliverable.setType(type);
        deliverable.setSubmissionDeadline(LocalDateTime.now(ZoneId.of("UTC")).plusDays(7));
        deliverable.setReviewDeadline(LocalDateTime.now(ZoneId.of("UTC")).plusDays(14));
        deliverable.setWeight(BigDecimal.valueOf(20));
        return deliverable;
    }

    private ProjectGroup newGroup(String name) {
        ProjectGroup group = new ProjectGroup();
        group.setGroupName(name);
        group.setStatus(GroupStatus.ADVISOR_ASSIGNED);
        group.setTermId("2026-SPRING");
        group.setCreatedAt(LocalDateTime.now());
        group.setVersion(0L);
        return group;
    }

    private RubricCriterion newRubric(Deliverable deliverable, String criterionName, String weight) {
        RubricCriterion rubric = new RubricCriterion();
        rubric.setDeliverable(deliverable);
        rubric.setCriterionName(criterionName);
        rubric.setGradingType(RubricCriterion.GradingType.Soft);
        rubric.setWeight(new BigDecimal(weight));
        return rubric;
    }
}