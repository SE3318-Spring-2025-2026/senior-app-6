package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.StaffUser;

/**
 * Verifies AdvisorRequestRepository query methods.
 *
 * Critical methods under test:
 *   findTopByGroupIdOrderBySentAtDesc  — P3 Rule 7: used for getAdvisorRequest/cancel (not findByGroupIdAndStatus)
 *   bulkUpdateStatusForGroup           — exclude one ID (accept path: auto-reject competing requests)
 *   bulkUpdateStatusByGroupId          — update ALL (sanitization + coordinator assign)
 */
@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class AdvisorRequestRepositoryTest extends RepositoryTestBase {

    @Autowired
    AdvisorRequestRepository repo;

    // ── findTopByGroupIdOrderBySentAtDesc ─────────────────────────────────────
    // P3 Rule 7: this is the primary lookup for getAdvisorRequest and cancelAdvisorRequest

    @Test
    void findTopByGroupIdOrderBySentAtDesc_returnsMostRecentRequest() {
        StaffUser prof = makeProfessor("prof1@test.com");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        LocalDateTime earlier = LocalDateTime.now().minusHours(2);
        LocalDateTime later = LocalDateTime.now().minusHours(1);

        makeAdvisorRequest(group, prof, RequestStatus.REJECTED, earlier);
        AdvisorRequest recent = makeAdvisorRequest(group, prof, RequestStatus.PENDING, later);

        var result = repo.findTopByGroupIdOrderBySentAtDesc(group.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(recent.getId());
    }

    @Test
    void findTopByGroupIdOrderBySentAtDesc_emptyWhenNoRequests() {
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        assertThat(repo.findTopByGroupIdOrderBySentAtDesc(group.getId())).isEmpty();
    }

    // ── bulkUpdateStatusForGroup (with excludeId) ─────────────────────────────
    // Used on advisor ACCEPT: auto-reject all OTHER pending requests for same group, preserve accepted one

    @Test
    void bulkUpdateStatusForGroup_updatesAllPendingExceptExcluded() {
        StaffUser prof1 = makeProfessor("prof2@test.com");
        StaffUser prof2 = makeProfessor("prof3@test.com");
        StaffUser prof3 = makeProfessor("prof4@test.com");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        AdvisorRequest accepted = makeAdvisorRequest(group, prof1, RequestStatus.PENDING, LocalDateTime.now().minusMinutes(10));
        AdvisorRequest other1 = makeAdvisorRequest(group, prof2, RequestStatus.PENDING, LocalDateTime.now().minusMinutes(5));
        AdvisorRequest other2 = makeAdvisorRequest(group, prof3, RequestStatus.PENDING, LocalDateTime.now());

        repo.bulkUpdateStatusForGroup(RequestStatus.AUTO_REJECTED, group.getId(), accepted.getId());
        clearCache();

        assertThat(repo.findById(accepted.getId()).get().getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(repo.findById(other1.getId()).get().getStatus()).isEqualTo(RequestStatus.AUTO_REJECTED);
        assertThat(repo.findById(other2.getId()).get().getStatus()).isEqualTo(RequestStatus.AUTO_REJECTED);
    }

    @Test
    void bulkUpdateStatusForGroup_doesNotAffectNonPendingRequests() {
        StaffUser prof1 = makeProfessor("prof5@test.com");
        StaffUser prof2 = makeProfessor("prof6@test.com");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        AdvisorRequest accepted = makeAdvisorRequest(group, prof1, RequestStatus.PENDING, LocalDateTime.now().minusMinutes(5));
        AdvisorRequest alreadyRejected = makeAdvisorRequest(group, prof2, RequestStatus.REJECTED, LocalDateTime.now());

        repo.bulkUpdateStatusForGroup(RequestStatus.AUTO_REJECTED, group.getId(), accepted.getId());
        clearCache();

        // REJECTED should stay REJECTED, not changed to AUTO_REJECTED
        assertThat(repo.findById(alreadyRejected.getId()).get().getStatus()).isEqualTo(RequestStatus.REJECTED);
    }

    // ── bulkUpdateStatusByGroupId (no excludeId) ──────────────────────────────
    // Used by SanitizationService (seq 3.4) and coordinator assign (seq 3.5): update ALL pending

    @Test
    void bulkUpdateStatusByGroupId_updatesAllPendingRequestsForGroup() {
        StaffUser prof1 = makeProfessor("prof7@test.com");
        StaffUser prof2 = makeProfessor("prof8@test.com");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        AdvisorRequest r1 = makeAdvisorRequest(group, prof1, RequestStatus.PENDING, LocalDateTime.now().minusMinutes(5));
        AdvisorRequest r2 = makeAdvisorRequest(group, prof2, RequestStatus.PENDING, LocalDateTime.now());

        repo.bulkUpdateStatusByGroupId(RequestStatus.AUTO_REJECTED, group.getId());
        clearCache();

        assertThat(repo.findById(r1.getId()).get().getStatus()).isEqualTo(RequestStatus.AUTO_REJECTED);
        assertThat(repo.findById(r2.getId()).get().getStatus()).isEqualTo(RequestStatus.AUTO_REJECTED);
    }

    @Test
    void bulkUpdateStatusByGroupId_doesNotAffectOtherGroups() {
        StaffUser prof = makeProfessor("prof9@test.com");
        ProjectGroup groupA = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);
        ProjectGroup groupB = makeGroup("Group B", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        makeAdvisorRequest(groupA, prof, RequestStatus.PENDING, LocalDateTime.now());
        AdvisorRequest groupBRequest = makeAdvisorRequest(groupB, prof, RequestStatus.PENDING, LocalDateTime.now());

        repo.bulkUpdateStatusByGroupId(RequestStatus.AUTO_REJECTED, groupA.getId());
        clearCache();

        // Group B's request must not be touched
        assertThat(repo.findById(groupBRequest.getId()).get().getStatus()).isEqualTo(RequestStatus.PENDING);
    }

    // ── findByAdvisorIdAndStatus ──────────────────────────────────────────────
    // Used by professor inbox (seq 3.2)

    @Test
    void findByAdvisorIdAndStatus_returnsPendingRequestsForAdvisor() {
        StaffUser prof = makeProfessor("prof10@test.com");
        StaffUser otherProf = makeProfessor("prof11@test.com");
        ProjectGroup g1 = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);
        ProjectGroup g2 = makeGroup("Group B", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);
        ProjectGroup g3 = makeGroup("Group C", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        makeAdvisorRequest(g1, prof, RequestStatus.PENDING, LocalDateTime.now());
        makeAdvisorRequest(g2, prof, RequestStatus.REJECTED, LocalDateTime.now()); // not PENDING
        makeAdvisorRequest(g3, otherProf, RequestStatus.PENDING, LocalDateTime.now()); // different advisor

        var result = repo.findByAdvisorIdAndStatus(prof.getId(), RequestStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroup().getId()).isEqualTo(g1.getId());
    }

    // ── findByGroupIdAndStatus ────────────────────────────────────────────────
    // Used for duplicate-request check in sendAdvisorRequest (seq 3.1)

    @Test
    void findByGroupIdAndStatus_returnsPendingRequestIfExists() {
        StaffUser prof = makeProfessor("prof12@test.com");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        AdvisorRequest pending = makeAdvisorRequest(group, prof, RequestStatus.PENDING, LocalDateTime.now());

        var result = repo.findByGroupIdAndStatus(group.getId(), RequestStatus.PENDING);
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(pending.getId());
    }

    @Test
    void findByGroupIdAndStatus_emptyWhenNoPendingRequest() {
        StaffUser prof = makeProfessor("prof13@test.com");
        ProjectGroup group = makeGroup("Group A", "2024-FALL", ProjectGroup.GroupStatus.TOOLS_BOUND);

        makeAdvisorRequest(group, prof, RequestStatus.REJECTED, LocalDateTime.now());

        assertThat(repo.findByGroupIdAndStatus(group.getId(), RequestStatus.PENDING)).isEmpty();
    }
}
