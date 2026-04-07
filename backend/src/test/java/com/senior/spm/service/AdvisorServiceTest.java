package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AdvisorAtCapacityException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.RequestNotFoundException;
import com.senior.spm.exception.RequestNotPendingException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.service.dto.AdvisorRequestDetail;
import com.senior.spm.service.dto.AdvisorRequestSummary;
import com.senior.spm.service.dto.AdvisorRespondResponse;

@ExtendWith(MockitoExtension.class)
class AdvisorServiceTest {

    @Mock private AdvisorRequestRepository advisorRequestRepository;
    @Mock private ProjectGroupRepository projectGroupRepository;
    @Mock private GroupMembershipRepository groupMembershipRepository;
    @Mock private TermConfigService termConfigService;

    @InjectMocks
    private AdvisorService advisorService;

    // ── constants ──────────────────────────────────────────────────────────────

    private static final String TERM_ID         = "2024-FALL";
    private static final int    CAPACITY        = 5;
    private static final UUID   PROFESSOR_ID    = UUID.randomUUID();
    private static final UUID   OTHER_PROF_ID   = UUID.randomUUID();
    private static final UUID   REQUEST_ID      = UUID.randomUUID();
    private static final UUID   GROUP_ID        = UUID.randomUUID();

    // ── shared fixtures ────────────────────────────────────────────────────────

    private StaffUser professor;
    private ProjectGroup group;
    private AdvisorRequest pendingRequest;

    @BeforeEach
    void setUp() {
        professor = new StaffUser();
        professor.setId(PROFESSOR_ID);
        professor.setAdvisorCapacity(CAPACITY);

        group = new ProjectGroup();
        group.setId(GROUP_ID);
        group.setGroupName("TeamAlpha");
        group.setTermId(TERM_ID);
        group.setStatus(GroupStatus.TOOLS_BOUND);
        group.setCreatedAt(LocalDateTime.now().minusDays(1));
        group.setVersion(0L);
        group.setMembers(List.of());

        pendingRequest = new AdvisorRequest();
        pendingRequest.setId(REQUEST_ID);
        pendingRequest.setAdvisor(professor);
        pendingRequest.setGroup(group);
        pendingRequest.setStatus(RequestStatus.PENDING);
        pendingRequest.setSentAt(LocalDateTime.now().minusHours(2));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getPendingRequestsForAdvisor
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getPendingRequests_twoRequests_returnsMappedSummaries() {
        AdvisorRequest req2 = buildPendingRequest(UUID.randomUUID(), buildGroup(UUID.randomUUID(), "TeamBeta", 2));
        when(advisorRequestRepository.findByAdvisorIdAndStatus(PROFESSOR_ID, RequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest, req2));

        List<AdvisorRequestSummary> result = advisorService.getPendingRequestsForAdvisor(PROFESSOR_ID);

        assertThat(result).hasSize(2);
        AdvisorRequestSummary first = result.get(0);
        assertThat(first.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(first.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(first.getGroupName()).isEqualTo("TeamAlpha");
        assertThat(first.getTermId()).isEqualTo(TERM_ID);
        assertThat(first.getMemberCount()).isEqualTo(0);
        assertThat(first.getSentAt()).isEqualTo(pendingRequest.getSentAt());
    }

    @Test
    void getPendingRequests_noPendingRequests_returnsEmptyList() {
        when(advisorRequestRepository.findByAdvisorIdAndStatus(PROFESSOR_ID, RequestStatus.PENDING))
                .thenReturn(List.of());

        List<AdvisorRequestSummary> result = advisorService.getPendingRequestsForAdvisor(PROFESSOR_ID);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void getPendingRequests_groupWithThreeMembers_memberCountIsThree() {
        group.setMembers(List.of(
                buildMembership(MemberRole.TEAM_LEADER),
                buildMembership(MemberRole.MEMBER),
                buildMembership(MemberRole.MEMBER)
        ));
        when(advisorRequestRepository.findByAdvisorIdAndStatus(PROFESSOR_ID, RequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest));
        when(groupMembershipRepository.countByGroupId(GROUP_ID)).thenReturn(3L);

        List<AdvisorRequestSummary> result = advisorService.getPendingRequestsForAdvisor(PROFESSOR_ID);

        assertThat(result.get(0).getMemberCount()).isEqualTo(3);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getRequestDetail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void getRequestDetail_success_returnsDetailWithGroupAndMembers() {
        Student student = buildStudent("22070006001");
        GroupMembership membership = buildMembershipForStudent(student, MemberRole.TEAM_LEADER);
        group.setMembers(List.of(membership));
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        AdvisorRequestDetail result = advisorService.getRequestDetail(REQUEST_ID, PROFESSOR_ID);

        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(result.getSentAt()).isEqualTo(pendingRequest.getSentAt());
        assertThat(result.getGroup().getId()).isEqualTo(GROUP_ID);
        assertThat(result.getGroup().getGroupName()).isEqualTo("TeamAlpha");
        assertThat(result.getGroup().getTermId()).isEqualTo(TERM_ID);
        assertThat(result.getGroup().getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
        assertThat(result.getGroup().getMembers()).hasSize(1);
        assertThat(result.getGroup().getMembers().get(0).getStudentId()).isEqualTo("22070006001");
        assertThat(result.getGroup().getMembers().get(0).getRole()).isEqualTo("TEAM_LEADER");
    }

    @Test
    void getRequestDetail_requestNotFound_throwsRequestNotFoundException() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> advisorService.getRequestDetail(REQUEST_ID, PROFESSOR_ID))
                .isInstanceOf(RequestNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getRequestDetail_wrongProfessor_throwsForbiddenException() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> advisorService.getRequestDetail(REQUEST_ID, OTHER_PROF_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not addressed to you");
    }

    @Test
    void getRequestDetail_notFoundCheckFiresBeforeOwnershipCheck() {
        // When the request doesn't exist, must get 404 regardless of professor ID
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        // Use OTHER_PROF_ID — should still get RequestNotFoundException, not ForbiddenException
        assertThatThrownBy(() -> advisorService.getRequestDetail(REQUEST_ID, OTHER_PROF_ID))
                .isInstanceOf(RequestNotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  respondToRequest — REJECT path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void respondToRequest_reject_setsStatusRejectedAndRespondedAt() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, false);

        ArgumentCaptor<AdvisorRequest> captor = ArgumentCaptor.forClass(AdvisorRequest.class);
        verify(advisorRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(captor.getValue().getRespondedAt()).isNotNull();
    }

    @Test
    void respondToRequest_reject_doesNotModifyGroupStatus() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, false);

        verify(projectGroupRepository, never()).save(any());
        assertThat(group.getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
    }

    @Test
    void respondToRequest_reject_doesNotCallBulkUpdateStatusForGroup() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, false);

        verify(advisorRequestRepository, never()).bulkUpdateStatusForGroup(any(), any(), any());
    }

    @Test
    void respondToRequest_reject_returnsDtoWithRejectedStatus() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        AdvisorRespondResponse response = advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, false);

        assertThat(response.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(response.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(response.getGroupId()).isNull();
        assertThat(response.getGroupStatus()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  respondToRequest — shared guards (fire for both accept and reject)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void respondToRequest_requestNotFound_throwsRequestNotFoundException() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(RequestNotFoundException.class)
                .hasMessageContaining("not found");

        verify(advisorRequestRepository, never()).save(any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void respondToRequest_wrongProfessor_throwsForbiddenException() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, OTHER_PROF_ID, true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not addressed to you");

        verify(advisorRequestRepository, never()).save(any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void respondToRequest_ownershipCheckFiresBeforeStatusCheck() {
        // Set request to non-PENDING state AND wrong professor
        // Must get ForbiddenException (ownership), NOT RequestNotPendingException
        pendingRequest.setStatus(RequestStatus.REJECTED);
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, OTHER_PROF_ID, true))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void respondToRequest_requestAlreadyRejected_throwsRequestNotPendingException() {
        pendingRequest.setStatus(RequestStatus.REJECTED);
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(RequestNotPendingException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void respondToRequest_requestAutoRejected_throwsRequestNotPendingException() {
        pendingRequest.setStatus(RequestStatus.AUTO_REJECTED);
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(RequestNotPendingException.class);
    }

    @Test
    void respondToRequest_requestAlreadyAccepted_throwsRequestNotPendingException() {
        pendingRequest.setStatus(RequestStatus.ACCEPTED);
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(RequestNotPendingException.class);
    }

    @Test
    void respondToRequest_requestCancelled_throwsRequestNotPendingException() {
        pendingRequest.setStatus(RequestStatus.CANCELLED);
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(RequestNotPendingException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  respondToRequest — ACCEPT path: capacity guard
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void respondToRequest_accept_advisorAtExactCapacity_throwsAdvisorAtCapacityException() {
        // capacity = 5, current = 5 → at limit
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED))
                .thenReturn((long) CAPACITY); // 5 >= 5

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(AdvisorAtCapacityException.class)
                .hasMessageContaining("maximum group capacity");
    }

    @Test
    void respondToRequest_accept_advisorOverCapacity_throwsAdvisorAtCapacityException() {
        // capacity = 5, current = 6 → over limit (edge case overflow)
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED))
                .thenReturn((long) CAPACITY + 1);

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(AdvisorAtCapacityException.class);
    }

    @Test
    void respondToRequest_accept_atCapacity_requestStaysPending() {
        // P3 rule #1: request must NOT be auto-rejected when advisor is at capacity
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED))
                .thenReturn((long) CAPACITY);

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(AdvisorAtCapacityException.class);

        // Request must not be saved (stays PENDING — advisor can accept later if load drops)
        verify(advisorRequestRepository, never()).save(any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void respondToRequest_accept_atCapacity_groupNotModified() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED))
                .thenReturn((long) CAPACITY);

        assertThatThrownBy(() -> advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true))
                .isInstanceOf(AdvisorAtCapacityException.class);

        assertThat(group.getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
        assertThat(group.getAdvisor()).isNull();
    }

    @Test
    void respondToRequest_accept_oneBelowCapacity_succeeds() {
        // capacity = 5, current = 4 → one slot available
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED))
                .thenReturn((long) CAPACITY - 1); // 4 < 5

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        verify(projectGroupRepository).save(group);
    }

    @Test
    void respondToRequest_accept_capacityQueryExcludesDisbandedGroups() {
        // The query MUST use StatusNot(DISBANDED), not plain countByAdvisorIdAndTermId
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED))
                .thenReturn(0L);

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        verify(projectGroupRepository).countByAdvisorIdAndTermIdAndStatusNot(
                PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED);
    }

    @Test
    void respondToRequest_accept_capacityQueryUsesActiveTermFromTermConfigService() {
        String differentTerm = "2025-SPRING";
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(differentTerm);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, differentTerm, GroupStatus.DISBANDED))
                .thenReturn(0L);

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        verify(termConfigService).getActiveTermId();
        verify(projectGroupRepository).countByAdvisorIdAndTermIdAndStatusNot(
                eq(PROFESSOR_ID), eq(differentTerm), eq(GroupStatus.DISBANDED));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  respondToRequest — ACCEPT path: happy path writes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void respondToRequest_accept_setsRequestStatusAcceptedAndRespondedAt() {
        stubAcceptPrereqs();

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        ArgumentCaptor<AdvisorRequest> captor = ArgumentCaptor.forClass(AdvisorRequest.class);
        verify(advisorRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(captor.getValue().getRespondedAt()).isNotNull();
    }

    @Test
    void respondToRequest_accept_setsGroupAdvisorToProfessor() {
        stubAcceptPrereqs();

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getAdvisor()).isEqualTo(professor);
    }

    @Test
    void respondToRequest_accept_setsGroupStatusToAdvisorAssigned() {
        stubAcceptPrereqs();

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.ADVISOR_ASSIGNED);
    }

    @Test
    void respondToRequest_accept_bulkAutoRejectsOtherPendingRequestsForSameGroup() {
        stubAcceptPrereqs();

        advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        // Must pass AUTO_REJECTED, group's ID, and exclude the accepted request
        verify(advisorRequestRepository).bulkUpdateStatusForGroup(
                eq(RequestStatus.AUTO_REJECTED),
                eq(GROUP_ID),
                eq(REQUEST_ID)
        );
    }

    @Test
    void respondToRequest_accept_returnsCorrectDto() {
        stubAcceptPrereqs();

        AdvisorRespondResponse response = advisorService.respondToRequest(REQUEST_ID, PROFESSOR_ID, true);

        assertThat(response.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(response.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(response.getGroupStatus()).isEqualTo(GroupStatus.ADVISOR_ASSIGNED);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Stubs the happy-path prerequisites for an accept (capacity = 0 active groups). */
    private void stubAcceptPrereqs() {
        when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(PROFESSOR_ID, TERM_ID, GroupStatus.DISBANDED))
                .thenReturn(0L);
    }

    private ProjectGroup buildGroup(UUID id, String name, int memberCount) {
        ProjectGroup g = new ProjectGroup();
        g.setId(id);
        g.setGroupName(name);
        g.setTermId(TERM_ID);
        g.setStatus(GroupStatus.TOOLS_BOUND);
        g.setCreatedAt(LocalDateTime.now().minusDays(1));
        g.setVersion(0L);
        List<GroupMembership> members = new java.util.ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            members.add(buildMembership(MemberRole.MEMBER));
        }
        g.setMembers(members);
        return g;
    }

    private AdvisorRequest buildPendingRequest(UUID requestId, ProjectGroup g) {
        AdvisorRequest r = new AdvisorRequest();
        r.setId(requestId);
        r.setAdvisor(professor);
        r.setGroup(g);
        r.setStatus(RequestStatus.PENDING);
        r.setSentAt(LocalDateTime.now().minusHours(1));
        return r;
    }

    private GroupMembership buildMembership(MemberRole role) {
        GroupMembership m = new GroupMembership();
        m.setId(UUID.randomUUID());
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now().minusDays(1));
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setStudentId("2207000" + UUID.randomUUID().toString().substring(0, 4));
        m.setStudent(s);
        return m;
    }

    private GroupMembership buildMembershipForStudent(Student student, MemberRole role) {
        GroupMembership m = new GroupMembership();
        m.setId(UUID.randomUUID());
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now().minusDays(1));
        m.setStudent(student);
        return m;
    }

    private Student buildStudent(String studentId) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setStudentId(studentId);
        return s;
    }
}
