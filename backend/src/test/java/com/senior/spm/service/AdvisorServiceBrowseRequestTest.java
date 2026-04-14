package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.controller.response.AdvisorCapacityResponse;
import com.senior.spm.controller.response.AdvisorOverrideResponse;
import com.senior.spm.controller.response.AdvisorRequestResponse;
import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AdvisorAtCapacityException;
import com.senior.spm.exception.AdvisorNotFoundException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.DuplicateRequestException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.RequestNotFoundException;
import com.senior.spm.exception.ScheduleWindowClosedException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;
import com.senior.spm.repository.StaffUserRepository;

/**
 * Unit tests for the new AdvisorService methods added in Issue #59 and #62:
 * getAvailableAdvisors, sendAdvisorRequest, getAdvisorRequest, cancelAdvisorRequest,
 * getAllAdvisorsWithCapacity, assignAdvisor, removeAdvisor.
 *
 * Also contains the bug-proof test for the termId discrepancy in respondToRequest.
 */
@ExtendWith(MockitoExtension.class)
class AdvisorServiceBrowseRequestTest {

    @Mock private AdvisorRequestRepository    advisorRequestRepository;
    @Mock private ProjectGroupRepository      projectGroupRepository;
    @Mock private GroupMembershipRepository   groupMembershipRepository;
    @Mock private StaffUserRepository         staffUserRepository;
    @Mock private ScheduleWindowRepository    scheduleWindowRepository;
    @Mock private TermConfigService           termConfigService;

    @InjectMocks
    private AdvisorService advisorService;

    // ── shared constants ───────────────────────────────────────────────────────

    private static final String GROUP_TERM_ID  = "2024-FALL";
    private static final String ACTIVE_TERM_ID = "2024-FALL";
    private static final int    CAPACITY       = 5;

    private static final UUID STUDENT_UUID   = UUID.randomUUID();
    private static final UUID GROUP_ID       = UUID.randomUUID();
    private static final UUID ADVISOR_ID     = UUID.randomUUID();
    private static final UUID REQUEST_ID     = UUID.randomUUID();

    // ── shared fixtures ────────────────────────────────────────────────────────

    private StaffUser      professor;
    private ProjectGroup   group;
    private GroupMembership leaderMembership;

    @BeforeEach
    void setUp() {
        professor = new StaffUser();
        professor.setId(ADVISOR_ID);
        professor.setMail("advisor@university.edu");
        professor.setRole(StaffUser.Role.Professor);
        professor.setAdvisorCapacity(CAPACITY);

        group = new ProjectGroup();
        group.setId(GROUP_ID);
        group.setGroupName("TeamAlpha");
        group.setTermId(GROUP_TERM_ID);
        group.setStatus(GroupStatus.TOOLS_BOUND);
        group.setCreatedAt(LocalDateTime.now().minusDays(1));
        group.setVersion(0L);
        group.setMembers(List.of());

        leaderMembership = new GroupMembership();
        leaderMembership.setId(UUID.randomUUID());
        leaderMembership.setGroup(group);
        leaderMembership.setRole(MemberRole.TEAM_LEADER);
        leaderMembership.setJoinedAt(LocalDateTime.now().minusDays(1));
        Student leader = new Student();
        leader.setId(STUDENT_UUID);
        leader.setStudentId("22070006001");
        leaderMembership.setStudent(leader);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAvailableAdvisors
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetAvailableAdvisors {

        @Test
        void returnsOnlyAdvisorsBelowCapacity() {
            StaffUser full = buildProfessor(UUID.randomUUID(), "full@uni.edu", 5);
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor, full));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn(2L);
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    full.getId(), ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn((long) CAPACITY);

            List<AdvisorCapacityResponse> result = advisorService.getAvailableAdvisors();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAdvisorId()).isEqualTo(ADVISOR_ID);
        }

        @Test
        void atCapacityAdvisorIsExcluded() {
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn((long) CAPACITY);

            List<AdvisorCapacityResponse> result = advisorService.getAvailableAdvisors();

            assertThat(result).isEmpty();
        }

        @Test
        void atCapacityFieldIsNullInStudentFacingResponse() {
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn(2L);

            List<AdvisorCapacityResponse> result = advisorService.getAvailableAdvisors();

            assertThat(result.get(0).getAtCapacity()).isNull();
        }

        @Test
        void responseContainsCorrectCapacityFields() {
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn(3L);

            AdvisorCapacityResponse dto = advisorService.getAvailableAdvisors().get(0);

            assertThat(dto.getAdvisorId()).isEqualTo(ADVISOR_ID);
            assertThat(dto.getMail()).isEqualTo("advisor@university.edu");
            assertThat(dto.getCurrentGroupCount()).isEqualTo(3);
            assertThat(dto.getCapacity()).isEqualTo(CAPACITY);
        }

        @Test
        void capacityQueryExcludesDisbandedGroups() {
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn(0L);

            advisorService.getAvailableAdvisors();

            verify(projectGroupRepository).countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  sendAdvisorRequest
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class SendAdvisorRequest {

        @Test
        void requesterNotInGroup_throwsForbidden() {
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void requesterIsMemberButNotLeader_throwsForbidden() {
            GroupMembership member = buildMembership(STUDENT_UUID, MemberRole.MEMBER);
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(member));

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void groupNotFound_throwsGroupNotFoundException() {
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(GroupNotFoundException.class);
        }

        @Test
        void groupNotToolsBound_throwsBusinessRuleException() {
            group.setStatus(GroupStatus.FORMING);
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("TOOLS_BOUND");
        }

        @Test
        void windowNotConfigured_throwsScheduleWindowClosedException() {
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(scheduleWindowRepository.findByTermIdAndType(GROUP_TERM_ID, ScheduleWindow.WindowType.ADVISOR_ASSOCIATION))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(ScheduleWindowClosedException.class);
        }

        @Test
        void windowNotYetOpen_throwsScheduleWindowClosedException() {
            stubWindowOpen(LocalDateTime.now(ZoneId.of("UTC")).plusHours(1),
                           LocalDateTime.now(ZoneId.of("UTC")).plusHours(48));
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(ScheduleWindowClosedException.class);
        }

        @Test
        void windowAlreadyClosed_throwsScheduleWindowClosedException() {
            stubWindowOpen(LocalDateTime.now(ZoneId.of("UTC")).minusDays(10),
                           LocalDateTime.now(ZoneId.of("UTC")).minusDays(1));
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(ScheduleWindowClosedException.class);
        }

        @Test
        void duplicatePendingRequest_throwsDuplicateRequestException() {
            stubWindowOpen(windowActive());
            stubLeaderAndGroup();
            when(advisorRequestRepository.findByGroupIdAndStatus(GROUP_ID, RequestStatus.PENDING))
                    .thenReturn(Optional.of(buildRequest(RequestStatus.PENDING)));

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(DuplicateRequestException.class);
        }

        @Test
        void advisorNotFound_throwsAdvisorNotFoundException() {
            stubWindowOpen(windowActive());
            stubLeaderAndGroup();
            when(advisorRequestRepository.findByGroupIdAndStatus(GROUP_ID, RequestStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(AdvisorNotFoundException.class);
        }

        @Test
        void advisorIsNotProfessor_throwsAdvisorNotFoundException() {
            StaffUser coordinator = buildProfessor(ADVISOR_ID, "coord@uni.edu", 5);
            coordinator.setRole(StaffUser.Role.Coordinator);
            stubWindowOpen(windowActive());
            stubLeaderAndGroup();
            when(advisorRequestRepository.findByGroupIdAndStatus(GROUP_ID, RequestStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(coordinator));

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(AdvisorNotFoundException.class);
        }

        @Test
        void advisorAtCapacity_throwsAdvisorAtCapacityException() {
            stubWindowOpen(windowActive());
            stubLeaderAndGroup();
            when(advisorRequestRepository.findByGroupIdAndStatus(GROUP_ID, RequestStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, GROUP_TERM_ID, GroupStatus.DISBANDED)).thenReturn((long) CAPACITY);

            assertThatThrownBy(() -> advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID))
                    .isInstanceOf(AdvisorAtCapacityException.class);
        }

        @Test
        void happyPath_persistsRequestWithPendingStatusAndCorrectFields() {
            stubSendHappyPath();
            AdvisorRequest saved = buildRequest(RequestStatus.PENDING);
            when(advisorRequestRepository.save(any())).thenReturn(saved);

            advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID);

            ArgumentCaptor<AdvisorRequest> captor = ArgumentCaptor.forClass(AdvisorRequest.class);
            verify(advisorRequestRepository).save(captor.capture());
            AdvisorRequest persisted = captor.getValue();
            assertThat(persisted.getStatus()).isEqualTo(RequestStatus.PENDING);
            assertThat(persisted.getGroup()).isEqualTo(group);
            assertThat(persisted.getAdvisor()).isEqualTo(professor);
            assertThat(persisted.getSentAt()).isNotNull();
        }

        @Test
        void happyPath_returns201ResponseWithCorrectFields() {
            stubSendHappyPath();
            AdvisorRequest saved = buildRequest(RequestStatus.PENDING);
            when(advisorRequestRepository.save(any())).thenReturn(saved);

            AdvisorRequestResponse response = advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID);

            assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(response.getAdvisorId()).isEqualTo(ADVISOR_ID);
            assertThat(response.getStatus()).isEqualTo(RequestStatus.PENDING);
            assertThat(response.getSentAt()).isNotNull();
        }

        /**
         * BUG PROOF: capacity check in sendAdvisorRequest must use group.getTermId(),
         * not termConfigService.getActiveTermId(). If the active term has rolled over,
         * the capacity count must still be scoped to the group's own term.
         *
         * This test verifies the corrected behavior (passes after the fix applied in
         * commit fix(#59)).
         */
        @Test
        void capacityCheck_usesGroupTermIdNotActiveTermId() {
            String differentActiveTerm = "2025-SPRING";
            // Active term is different from the group's term
            // Capacity query must use GROUP_TERM_ID ("2024-FALL"), not "2025-SPRING"
            stubWindowOpen(windowActive());
            stubLeaderAndGroup();
            when(advisorRequestRepository.findByGroupIdAndStatus(GROUP_ID, RequestStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, GROUP_TERM_ID, GroupStatus.DISBANDED)).thenReturn(0L);
            AdvisorRequest saved = buildRequest(RequestStatus.PENDING);
            when(advisorRequestRepository.save(any())).thenReturn(saved);

            advisorService.sendAdvisorRequest(GROUP_ID, ADVISOR_ID, STUDENT_UUID);

            // Must query with the group's termId
            verify(projectGroupRepository).countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, GROUP_TERM_ID, GroupStatus.DISBANDED);
            // Must NOT query with the active term (would fail if called with differentActiveTerm)
            verify(projectGroupRepository, never()).countByAdvisorIdAndTermIdAndStatusNot(
                    eq(ADVISOR_ID), eq(differentActiveTerm), any());
        }

        private void stubLeaderAndGroup() {
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        }

        private void stubSendHappyPath() {
            stubWindowOpen(windowActive());
            stubLeaderAndGroup();
            when(advisorRequestRepository.findByGroupIdAndStatus(GROUP_ID, RequestStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, GROUP_TERM_ID, GroupStatus.DISBANDED)).thenReturn(0L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAdvisorRequest
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetAdvisorRequest {

        @Test
        void notMember_throwsForbidden() {
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.getAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void noRequestExists_throwsRequestNotFoundException() {
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.getAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(RequestNotFoundException.class);
        }

        @Test
        void success_returnsResponseWithAdvisorName() {
            AdvisorRequest request = buildRequest(RequestStatus.PENDING);
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.of(request));

            AdvisorRequestResponse response = advisorService.getAdvisorRequest(GROUP_ID, STUDENT_UUID);

            assertThat(response.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(response.getAdvisorId()).isEqualTo(ADVISOR_ID);
            assertThat(response.getAdvisorName()).isEqualTo("advisor@university.edu");
            assertThat(response.getStatus()).isEqualTo(RequestStatus.PENDING);
        }

        @Test
        void success_returnsRespondedAtWhenPresent() {
            AdvisorRequest request = buildRequest(RequestStatus.REJECTED);
            LocalDateTime respondedAt = LocalDateTime.now(ZoneId.of("UTC")).minusHours(1);
            request.setRespondedAt(respondedAt);
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.of(request));

            AdvisorRequestResponse response = advisorService.getAdvisorRequest(GROUP_ID, STUDENT_UUID);

            assertThat(response.getRespondedAt()).isEqualTo(respondedAt);
        }

        @Test
        void anyMemberCanViewRequest_notJustLeader() {
            GroupMembership memberMembership = buildMembership(STUDENT_UUID, MemberRole.MEMBER);
            AdvisorRequest request = buildRequest(RequestStatus.PENDING);
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(memberMembership));
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.of(request));

            // Should not throw — any member can view, not just team leader
            AdvisorRequestResponse response = advisorService.getAdvisorRequest(GROUP_ID, STUDENT_UUID);
            assertThat(response).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  cancelAdvisorRequest
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class CancelAdvisorRequest {

        @Test
        void groupNotFound_throwsGroupNotFoundException() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(GroupNotFoundException.class);
        }

        @Test
        void groupNotToolsBound_throwsBusinessRuleException_raceConditionGuard() {
            // Race condition guard: if group status changed to ADVISOR_ASSIGNED while cancel is in flight,
            // cancel must be rejected to prevent overwriting the accepted request status.
            group.setStatus(GroupStatus.ADVISOR_ASSIGNED);
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("TOOLS_BOUND");
        }

        @Test
        void requesterNotMember_throwsForbidden() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void requesterNotLeader_throwsForbidden() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(buildMembership(STUDENT_UUID, MemberRole.MEMBER)));

            assertThatThrownBy(() -> advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void noRequestExists_throwsRequestNotFoundException_notBusiness() {
            // CLAUDE.md rule: use findTopByGroupIdOrderBySentAtDesc first.
            // If empty → 404 (RequestNotFoundException), not 400.
            stubGroupAndLeader();
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(RequestNotFoundException.class);
        }

        @Test
        void requestNotPending_throwsBusinessRuleException_not404() {
            // CLAUDE.md rule: if found but status != PENDING → 400 (BusinessRuleException).
            AdvisorRequest rejected = buildRequest(RequestStatus.REJECTED);
            stubGroupAndLeader();
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.of(rejected));

            // Must be BusinessRuleException (400), not RequestNotFoundException (404)
            assertThatThrownBy(() -> advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("pending");
        }

        @Test
        void requestAutoRejected_throwsBusinessRuleException() {
            AdvisorRequest autoRejected = buildRequest(RequestStatus.AUTO_REJECTED);
            stubGroupAndLeader();
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.of(autoRejected));

            assertThatThrownBy(() -> advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void happyPath_setsStatusCancelledAndReturnsResponse() {
            AdvisorRequest pending = buildRequest(RequestStatus.PENDING);
            stubGroupAndLeader();
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.of(pending));
            when(advisorRequestRepository.save(any())).thenReturn(pending);

            AdvisorRequestResponse response = advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID);

            ArgumentCaptor<AdvisorRequest> captor = ArgumentCaptor.forClass(AdvisorRequest.class);
            verify(advisorRequestRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(response.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(response.getRequestId()).isEqualTo(REQUEST_ID);
        }

        @Test
        void happyPath_doesNotModifyGroup() {
            AdvisorRequest pending = buildRequest(RequestStatus.PENDING);
            stubGroupAndLeader();
            when(advisorRequestRepository.findTopByGroupIdOrderBySentAtDesc(GROUP_ID))
                    .thenReturn(Optional.of(pending));
            when(advisorRequestRepository.save(any())).thenReturn(pending);

            advisorService.cancelAdvisorRequest(GROUP_ID, STUDENT_UUID);

            verify(projectGroupRepository, never()).save(any());
        }

        private void stubGroupAndLeader() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_UUID))
                    .thenReturn(Optional.of(leaderMembership));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getAllAdvisorsWithCapacity (coordinator endpoint)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetAllAdvisorsWithCapacity {

        @Test
        void includesAdvisorsAtCapacity() {
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn((long) CAPACITY);

            List<AdvisorCapacityResponse> result = advisorService.getAllAdvisorsWithCapacity();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAtCapacity()).isTrue();
        }

        @Test
        void includesAdvisorsBelowCapacity_withAtCapacityFalse() {
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn(2L);

            List<AdvisorCapacityResponse> result = advisorService.getAllAdvisorsWithCapacity();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAtCapacity()).isFalse();
        }

        @Test
        void atCapacityFieldIsNotNullUnlikeStudentEndpoint() {
            when(termConfigService.getActiveTermId()).thenReturn(ACTIVE_TERM_ID);
            when(staffUserRepository.findByRole(StaffUser.Role.Professor))
                    .thenReturn(List.of(professor));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, ACTIVE_TERM_ID, GroupStatus.DISBANDED)).thenReturn(0L);

            List<AdvisorCapacityResponse> result = advisorService.getAllAdvisorsWithCapacity();

            assertThat(result.get(0).getAtCapacity()).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  assignAdvisor (coordinator override)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class AssignAdvisor {

        @Test
        void groupNotFound_throwsGroupNotFoundException() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID))
                    .isInstanceOf(GroupNotFoundException.class);
        }

        @Test
        void groupDisbanded_throwsBusinessRuleException() {
            group.setStatus(GroupStatus.DISBANDED);
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("disbanded");
        }

        @Test
        void groupInForming_throwsBusinessRuleException() {
            group.setStatus(GroupStatus.FORMING);
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void advisorNotFound_throwsAdvisorNotFoundException() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID))
                    .isInstanceOf(AdvisorNotFoundException.class);
        }

        @Test
        void sameAdvisorAlreadyAssigned_throwsBusinessRuleException() {
            group.setStatus(GroupStatus.ADVISOR_ASSIGNED);
            group.setAdvisor(professor);
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));

            assertThatThrownBy(() -> advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already");
        }

        @Test
        void happyPath_toolsBound_setsAdvisorAndStatusAdvisorAssigned() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));

            AdvisorOverrideResponse response = advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID);

            ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
            verify(projectGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getAdvisor()).isEqualTo(professor);
            assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.ADVISOR_ASSIGNED);
            assertThat(response.getStatus()).isEqualTo(GroupStatus.ADVISOR_ASSIGNED);
            assertThat(response.getAdvisorId()).isEqualTo(ADVISOR_ID);
        }

        @Test
        void happyPath_reassignDifferentAdvisor_allowed() {
            StaffUser oldAdvisor = buildProfessor(UUID.randomUUID(), "old@uni.edu", 5);
            group.setStatus(GroupStatus.ADVISOR_ASSIGNED);
            group.setAdvisor(oldAdvisor);
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));

            // Should not throw — reassigning a different advisor is allowed
            AdvisorOverrideResponse response = advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID);
            assertThat(response.getAdvisorId()).isEqualTo(ADVISOR_ID);
        }

        @Test
        void happyPath_bulkAutoRejectsAllPendingRequests() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));

            advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID);

            verify(advisorRequestRepository).bulkUpdateStatusByGroupId(
                    eq(RequestStatus.AUTO_REJECTED), eq(GROUP_ID));
        }

        @Test
        void noCapacityCheckPerformed_coordinatorBypassesCapacity() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(staffUserRepository.findById(ADVISOR_ID)).thenReturn(Optional.of(professor));

            advisorService.assignAdvisor(GROUP_ID, ADVISOR_ID);

            verify(projectGroupRepository, never()).countByAdvisorIdAndTermIdAndStatusNot(any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  removeAdvisor (coordinator override)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class RemoveAdvisor {

        @Test
        void groupNotFound_throwsGroupNotFoundException() {
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> advisorService.removeAdvisor(GROUP_ID))
                    .isInstanceOf(GroupNotFoundException.class);
        }

        @Test
        void groupHasNoAdvisor_throwsBusinessRuleException() {
            group.setAdvisor(null);
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            assertThatThrownBy(() -> advisorService.removeAdvisor(GROUP_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no advisor");
        }

        @Test
        void happyPath_clearsAdvisorAndRevertsToToolsBound() {
            group.setStatus(GroupStatus.ADVISOR_ASSIGNED);
            group.setAdvisor(professor);
            when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            AdvisorOverrideResponse response = advisorService.removeAdvisor(GROUP_ID);

            ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
            verify(projectGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getAdvisor()).isNull();
            assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
            assertThat(response.getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
            assertThat(response.getAdvisorId()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BUG PROOF: respondToRequest capacity termId discrepancy
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class RespondToRequestTermIdBug {

        /**
         * BUG: respondToRequest uses termConfigService.getActiveTermId() for the
         * capacity re-check, but it should use request.getGroup().getTermId() — the
         * same fix that was already applied to sendAdvisorRequest in commit fix(#59).
         *
         * Scenario: the group was created in "2024-FALL" but the active term has
         * rolled over to "2025-SPRING". The capacity re-check must still count groups
         * in "2024-FALL" (the group's own term), not "2025-SPRING".
         *
         * THIS TEST WILL FAIL on the current code and PASS after the fix.
         */
        @Test
        void accept_capacityCheckUsesGroupTermIdNotActiveTermId() {
            String groupTerm  = "2024-FALL";
            String activeTerm = "2025-SPRING";    // different from group's term

            group.setTermId(groupTerm);
            AdvisorRequest request = buildRequest(RequestStatus.PENDING);
            request.setGroup(group);

            when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
            // Capacity query with groupTerm returns 0 (pass)
            // NOTE: termConfigService.getActiveTermId() is intentionally NOT stubbed here —
            // the fix ensures it is never called; stubbing it would cause UnnecessaryStubbingException.
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, groupTerm, GroupStatus.DISBANDED)).thenReturn(0L);

            // Should succeed without throwing AdvisorAtCapacityException
            advisorService.respondToRequest(REQUEST_ID, ADVISOR_ID, true);

            // The capacity query MUST use the group's termId, not the active term
            verify(projectGroupRepository).countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, groupTerm, GroupStatus.DISBANDED);
            verify(projectGroupRepository, never()).countByAdvisorIdAndTermIdAndStatusNot(
                    eq(ADVISOR_ID), eq(activeTerm), any());
        }

        /**
         * Complementary: when terms match, the existing behavior is correct.
         * Ensures we don't break the common case while fixing the edge case.
         */
        @Test
        void accept_capacityCheckWhenTermsMatch_stillWorks() {
            AdvisorRequest request = buildRequest(RequestStatus.PENDING);

            when(advisorRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
            when(projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, GROUP_TERM_ID, GroupStatus.DISBANDED)).thenReturn(0L);

            advisorService.respondToRequest(REQUEST_ID, ADVISOR_ID, true);

            verify(projectGroupRepository).countByAdvisorIdAndTermIdAndStatusNot(
                    ADVISOR_ID, GROUP_TERM_ID, GroupStatus.DISBANDED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  helpers
    // ══════════════════════════════════════════════════════════════════════════

    private AdvisorRequest buildRequest(RequestStatus status) {
        AdvisorRequest r = new AdvisorRequest();
        r.setId(REQUEST_ID);
        r.setAdvisor(professor);
        r.setGroup(group);
        r.setStatus(status);
        r.setSentAt(LocalDateTime.now(ZoneId.of("UTC")).minusHours(1));
        return r;
    }

    private GroupMembership buildMembership(UUID studentId, MemberRole role) {
        Student s = new Student();
        s.setId(studentId);
        s.setStudentId("22070006001");
        GroupMembership m = new GroupMembership();
        m.setId(UUID.randomUUID());
        m.setRole(role);
        m.setStudent(s);
        m.setJoinedAt(LocalDateTime.now(ZoneId.of("UTC")).minusDays(1));
        return m;
    }

    private StaffUser buildProfessor(UUID id, String mail, int capacity) {
        StaffUser u = new StaffUser();
        u.setId(id);
        u.setMail(mail);
        u.setRole(StaffUser.Role.Professor);
        u.setAdvisorCapacity(capacity);
        return u;
    }

    private ScheduleWindow buildWindow(LocalDateTime opensAt, LocalDateTime closesAt) {
        ScheduleWindow w = new ScheduleWindow();
        w.setTermId(GROUP_TERM_ID);
        w.setType(ScheduleWindow.WindowType.ADVISOR_ASSOCIATION);
        w.setOpensAt(opensAt);
        w.setClosesAt(closesAt);
        return w;
    }

    /** Returns open/close timestamps for an active window. */
    private LocalDateTime[] windowActive() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        return new LocalDateTime[]{ now.minusDays(1), now.plusDays(7) };
    }

    private void stubWindowOpen(LocalDateTime[] times) {
        stubWindowOpen(times[0], times[1]);
    }

    private void stubWindowOpen(LocalDateTime opensAt, LocalDateTime closesAt) {
        when(scheduleWindowRepository.findByTermIdAndType(
                GROUP_TERM_ID, ScheduleWindow.WindowType.ADVISOR_ASSOCIATION))
                .thenReturn(Optional.of(buildWindow(opensAt, closesAt)));
    }
}
