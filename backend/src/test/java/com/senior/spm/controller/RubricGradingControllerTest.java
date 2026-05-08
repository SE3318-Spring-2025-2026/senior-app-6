package com.senior.spm.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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

import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.repository.CommitteeProfessorRepository;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.RubricGradeRepository;
import com.senior.spm.repository.StaffUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class RubricGradingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private DeliverableRepository deliverableRepository;
    @Autowired private DeliverableSubmissionRepository submissionRepository;
    @Autowired private RubricCriterionRepository criterionRepository;
    @Autowired private CommitteeRepository committeeRepository;
    @Autowired private CommitteeProfessorRepository committeeProfessorRepository;
    @Autowired private RubricGradeRepository rubricGradeRepository;

    private StaffUser professor;
    private Deliverable deliverable;
    private ProjectGroup group;
    private DeliverableSubmission submission;
    private RubricCriterion binaryCriterion;
    private RubricCriterion softCriterion;

    @BeforeEach
    void setUp() {
        rubricGradeRepository.deleteAll();
        committeeProfessorRepository.deleteAll();
        committeeRepository.deleteAll();
        submissionRepository.deleteAll();
        criterionRepository.deleteAll();
        projectGroupRepository.deleteAll();
        deliverableRepository.deleteAll();
        staffUserRepository.deleteAll();

        professor   = staffUserRepository.save(makeProfessor("prof@test.com"));
        deliverable = deliverableRepository.save(makeDeliverable("Proposal", 30));
        group       = projectGroupRepository.save(makeGroup("TeamAlpha"));
        submission  = submissionRepository.save(makeSubmission(group, deliverable));

        binaryCriterion = criterionRepository.save(makeCriterion(deliverable, "Correctness", RubricCriterion.GradingType.Binary, new BigDecimal("50")));
        softCriterion   = criterionRepository.save(makeCriterion(deliverable, "Quality",     RubricCriterion.GradingType.Soft,   new BigDecimal("50")));

        Committee committee = committeeRepository.save(makeCommittee(deliverable, group, "2026-SPRING"));
        committeeProfessorRepository.save(makeCommitteeProfessor(committee, professor));
    }

    @AfterEach
    void tearDown() {
        rubricGradeRepository.deleteAll();
        committeeProfessorRepository.deleteAll();
        committeeRepository.deleteAll();
        submissionRepository.deleteAll();
        criterionRepository.deleteAll();
        projectGroupRepository.deleteAll();
        deliverableRepository.deleteAll();
        staffUserRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Happy path — Binary criterion valid grade
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("First grade for Binary+Soft criteria returns 201 with baseDeliverableGrade")
    void firstGrade_binaryAndSoft_returns201WithBaseGrade() throws Exception {
        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(
                        entry(binaryCriterion.getId(), "S"),
                        entry(softCriterion.getId(),   "B"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseDeliverableGrade").isNumber());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Soft criterion — valid grade returns 201
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("First grade for Soft criteria with valid value returns 201")
    void firstGrade_softCriteria_validValue_returns201() throws Exception {
        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(
                        entry(binaryCriterion.getId(), "F"),
                        entry(softCriterion.getId(),   "A"))))
                .andExpect(status().isCreated());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Re-submit — same reviewer returns 200, no duplicate rows
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Second submission by same reviewer returns 200 and upserts — no duplicate rows")
    void resubmit_sameReviewer_returns200_noDuplicateRows() throws Exception {
        String firstPayload = gradesJson(
                entry(binaryCriterion.getId(), "S"),
                entry(softCriterion.getId(),   "A"));
        String updatePayload = gradesJson(
                entry(binaryCriterion.getId(), "F"),
                entry(softCriterion.getId(),   "C"));

        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDeliverableGrade").isNumber());

        // Exactly 2 rows (one per criterion), not 4
        long count = rubricGradeRepository.findBySubmissionId(submission.getId()).size();
        assert count == 2 : "Expected 2 rubric grade rows, got " + count;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Binary criterion receives invalid grade → 400
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Binary criterion receiving 'A' returns 400 with criterionId in message")
    void binaryCriterion_invalidGrade_returns400WithCriterionId() throws Exception {
        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(
                        entry(binaryCriterion.getId(), "A"),  // invalid for Binary
                        entry(softCriterion.getId(),   "B"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(binaryCriterion.getId().toString())));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Professor not on committee → 403
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Professor not assigned to committee returns 403")
    void professorNotOnCommittee_returns403() throws Exception {
        StaffUser outsider = staffUserRepository.save(makeProfessor("outsider@test.com"));

        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(outsider)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(
                        entry(binaryCriterion.getId(), "S"),
                        entry(softCriterion.getId(),   "A"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Submission not found → 404
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Unknown submissionId returns 404")
    void unknownSubmissionId_returns404() throws Exception {
        mockMvc.perform(post("/api/submissions/{id}/grade", UUID.randomUUID())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(
                        entry(binaryCriterion.getId(), "S"),
                        entry(softCriterion.getId(),   "A"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Criterion belongs to a different deliverable → 400
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Criterion from a different deliverable returns 400")
    void criterionFromDifferentDeliverable_returns400() throws Exception {
        Deliverable otherDeliverable = deliverableRepository.save(makeDeliverable("SoW", 20));
        RubricCriterion foreignCriterion = criterionRepository.save(
                makeCriterion(otherDeliverable, "Foreign", RubricCriterion.GradingType.Soft, new BigDecimal("100")));

        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(
                        entry(binaryCriterion.getId(), "S"),
                        entry(foreignCriterion.getId(), "A"))))  // wrong deliverable
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Student token → 403 (wrong role)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Student JWT returns 403 — endpoint is Professor-only")
    void studentToken_returns403() throws Exception {
        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(
                        entry(binaryCriterion.getId(), "S"),
                        entry(softCriterion.getId(),   "A"))))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Missing grade for one criterion → 400
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Payload missing one criterion returns 400")
    void missingCriterion_returns400() throws Exception {
        mockMvc.perform(post("/api/submissions/{id}/grade", submission.getId())
                .with(authentication(profAuth(professor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(gradesJson(entry(binaryCriterion.getId(), "S"))))  // softCriterion missing
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private StaffUser makeProfessor(String mail) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("hash");
        u.setRole(StaffUser.Role.Professor);
        u.setAdvisorCapacity(5);
        return u;
    }

    private Deliverable makeDeliverable(String name, int weight) {
        Deliverable d = new Deliverable();
        d.setName(name);
        d.setType(Deliverable.DeliverableType.Proposal);
        d.setWeight(new BigDecimal(weight));
        d.setSubmissionDeadline(LocalDateTime.now().plusDays(30));
        d.setReviewDeadline(LocalDateTime.now().plusDays(37));
        return d;
    }

    private ProjectGroup makeGroup(String name) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId("2026-SPRING");
        g.setStatus(ProjectGroup.GroupStatus.TOOLS_BOUND);
        g.setCreatedAt(LocalDateTime.now());
        return g;
    }

    private DeliverableSubmission makeSubmission(ProjectGroup g, Deliverable d) {
        DeliverableSubmission s = new DeliverableSubmission();
        s.setGroup(g);
        s.setDeliverable(d);
        s.setMarkdownContent("# Test submission");
        s.setSubmittedAt(LocalDateTime.now());
        s.setRevision(false);
        s.setRevisionNumber(0);
        return s;
    }

    private RubricCriterion makeCriterion(Deliverable d, String name,
            RubricCriterion.GradingType type, BigDecimal weight) {
        RubricCriterion c = new RubricCriterion();
        c.setDeliverable(d);
        c.setCriterionName(name);
        c.setGradingType(type);
        c.setWeight(weight);
        return c;
    }

    private Committee makeCommittee(Deliverable d, ProjectGroup g, String termId) {
        Committee c = new Committee();
        c.setCommitteeName("Test Committee");
        c.setTermId(termId);
        c.setDeliverable(d);
        c.setGroups(Set.of(g));
        return c;
    }

    private CommitteeProfessor makeCommitteeProfessor(Committee c, StaffUser p) {
        CommitteeProfessor cp = new CommitteeProfessor();
        cp.setCommittee(c);
        cp.setProfessor(p);
        cp.setRole(CommitteeProfessor.ProfessorRole.JURY);
        return cp;
    }

    private Authentication profAuth(StaffUser p) {
        return new UsernamePasswordAuthenticationToken(
                p.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR")));
    }

    private Authentication studentAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    private String entry(UUID criterionId, String grade) {
        return "{\"criterionId\":\"" + criterionId + "\",\"selectedGrade\":\"" + grade + "\"}";
    }

    private String gradesJson(String... entries) {
        return "{\"grades\":[" + String.join(",", entries) + "]}";
    }
}
