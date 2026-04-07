package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.StaffUser;

/**
 * Verifies ProjectGroupRepository query methods.
 *
 * Critical methods under test:
 *   countByAdvisorIdAndTermIdAndStatusNot — must EXCLUDE disbanded groups (P3 Rule 1)
 *   findByTermIdAndStatusNotAndAdvisorIsNull — used by SanitizationService (seq 3.4)
 */
@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class ProjectGroupRepositoryTest extends RepositoryTestBase {

    @Autowired
    ProjectGroupRepository repo;

    // ── existsByGroupNameAndTermId ────────────────────────────────────────────

    @Test
    void existsByGroupNameAndTermId_trueForDuplicateNameInSameTerm() {
        makeGroup("Team Alpha", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        assertThat(repo.existsByGroupNameAndTermId("Team Alpha", "2024-FALL")).isTrue();
    }

    @Test
    void existsByGroupNameAndTermId_falseForSameNameDifferentTerm() {
        makeGroup("Team Alpha", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        assertThat(repo.existsByGroupNameAndTermId("Team Alpha", "2025-SPRING")).isFalse();
    }

    @Test
    void existsByGroupNameAndTermId_falseForDifferentName() {
        makeGroup("Team Alpha", "2024-FALL", ProjectGroup.GroupStatus.FORMING);

        assertThat(repo.existsByGroupNameAndTermId("Team Beta", "2024-FALL")).isFalse();
    }

    // ── countByAdvisorIdAndTermIdAndStatusNot ─────────────────────────────────
    // SPEC: must exclude DISBANDED groups — plain countByAdvisorIdAndTermId inflates load

    @Test
    void countByAdvisorIdAndTermIdAndStatusNot_countsActiveGroupsForAdvisor() {
        StaffUser prof = makeProfessor("prof1@test.com");

        makeGroupWithAdvisor("Group A", "2024-FALL", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof);
        makeGroupWithAdvisor("Group B", "2024-FALL", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof);

        long count = repo.countByAdvisorIdAndTermIdAndStatusNot(
                prof.getId(), "2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countByAdvisorIdAndTermIdAndStatusNot_excludesDisbandedGroups() {
        StaffUser prof = makeProfessor("prof2@test.com");

        makeGroupWithAdvisor("Group A", "2024-FALL", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof);
        makeGroupWithAdvisor("Group Disbanded", "2024-FALL", ProjectGroup.GroupStatus.DISBANDED, prof);

        long count = repo.countByAdvisorIdAndTermIdAndStatusNot(
                prof.getId(), "2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        assertThat(count).isEqualTo(1); // disbanded group must NOT be counted
    }

    @Test
    void countByAdvisorIdAndTermIdAndStatusNot_doesNotCountOtherAdvisorsGroups() {
        StaffUser prof1 = makeProfessor("prof3@test.com");
        StaffUser prof2 = makeProfessor("prof4@test.com");

        makeGroupWithAdvisor("Group A", "2024-FALL", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof1);
        makeGroupWithAdvisor("Group B", "2024-FALL", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof2);

        long count = repo.countByAdvisorIdAndTermIdAndStatusNot(
                prof1.getId(), "2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByAdvisorIdAndTermIdAndStatusNot_doesNotCountOtherTermGroups() {
        StaffUser prof = makeProfessor("prof5@test.com");

        makeGroupWithAdvisor("Group A", "2024-FALL", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof);
        makeGroupWithAdvisor("Group B", "2025-SPRING", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof);

        long count = repo.countByAdvisorIdAndTermIdAndStatusNot(
                prof.getId(), "2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        assertThat(count).isEqualTo(1);
    }

    // ── findByTermIdAndStatusNotAndAdvisorIsNull ──────────────────────────────
    // Used by SanitizationService to find groups without advisor that need disbanding

    @Test
    void findByTermIdAndStatusNotAndAdvisorIsNull_returnsGroupsWithNoAdvisor() {
        ProjectGroup noAdvisor = makeGroup("No Advisor", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);
        StaffUser prof = makeProfessor("prof6@test.com");
        makeGroupWithAdvisor("Has Advisor", "2024-FALL", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED, prof);

        var result = repo.findByTermIdAndStatusNotAndAdvisorIsNull("2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(noAdvisor.getId());
    }

    @Test
    void findByTermIdAndStatusNotAndAdvisorIsNull_excludesAlreadyDisbandedGroups() {
        makeGroup("Active", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeGroup("Already Disbanded", "2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        var result = repo.findByTermIdAndStatusNotAndAdvisorIsNull("2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isEqualTo("Active");
    }

    @Test
    void findByTermIdAndStatusNotAndAdvisorIsNull_excludesOtherTerms() {
        makeGroup("Fall Group", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeGroup("Spring Group", "2025-SPRING", ProjectGroup.GroupStatus.FORMING);

        var result = repo.findByTermIdAndStatusNotAndAdvisorIsNull("2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isEqualTo("Fall Group");
    }

    // ── findByTermId ──────────────────────────────────────────────────────────

    @Test
    void findByTermId_returnsAllGroupsForTerm() {
        makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeGroup("Group B", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeGroup("Group C", "2025-SPRING", ProjectGroup.GroupStatus.FORMING);

        assertThat(repo.findByTermId("2024-FALL")).hasSize(2);
    }

    // ── findByTermIdAndStatus ─────────────────────────────────────────────────

    @Test
    void findByTermIdAndStatus_returnsOnlyMatchingStatus() {
        makeGroup("Forming", "2024-FALL", ProjectGroup.GroupStatus.FORMING);
        makeGroup("Tools Bound", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);
        makeGroup("Disbanded", "2024-FALL", ProjectGroup.GroupStatus.DISBANDED);

        var result = repo.findByTermIdAndStatus("2024-FALL", ProjectGroup.GroupStatus.FORMING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isEqualTo("Forming");
    }
}
