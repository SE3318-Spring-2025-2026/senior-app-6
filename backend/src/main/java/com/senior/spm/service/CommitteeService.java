package com.senior.spm.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.ProfessorAssignmentRequest;
import com.senior.spm.controller.response.CommitteeDetailResponse;
import com.senior.spm.controller.response.CommitteeGroupSummaryResponse;
import com.senior.spm.controller.response.CommitteeSummaryResponse;
import com.senior.spm.controller.response.ProfessorAssignmentResponse;
import com.senior.spm.controller.response.ProfessorCommitteeSummaryResponse;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.CommitteeProfessorRepository;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommitteeService {

    private final CommitteeRepository committeeRepository;
    private final CommitteeProfessorRepository committeeProfessorRepository;
    private final StaffUserRepository staffUserRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final CommitteeNotificationService committeeNotificationService;

    @Transactional
    public CommitteeSummaryResponse createCommittee(String committeeName, String termId) {
        if (committeeRepository.existsByCommitteeNameAndTermId(committeeName, termId)) {
            throw new AlreadyExistsException("A committee with the same name already exists for this term");
        }

        Committee committee = new Committee();
        committee.setCommitteeName(committeeName);
        committee.setTermId(termId);

        Committee saved = committeeRepository.save(committee);
        return new CommitteeSummaryResponse(saved.getId(), saved.getCommitteeName(), saved.getTermId());
    }

    @Transactional(readOnly = true)
    public List<CommitteeSummaryResponse> listCommittees(String termId) {
        List<Committee> committees = (termId == null || termId.isBlank())
                ? committeeRepository.findAll()
                : committeeRepository.findByTermId(termId);

        return committees.stream()
                .map(c -> new CommitteeSummaryResponse(c.getId(), c.getCommitteeName(), c.getTermId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CommitteeDetailResponse getCommitteeDetail(UUID committeeId) {
        Committee committee = committeeRepository.findDetailedById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found"));

        return toDetailResponse(committee);
    }

    @Transactional
    public CommitteeDetailResponse assignProfessors(UUID committeeId, List<ProfessorAssignmentRequest> professorRequests) {
        Committee committee = committeeRepository.findDetailedById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found"));

        validateProfessorAssignments(committee, professorRequests);

        committeeProfessorRepository.deleteByCommitteeId(committeeId);

        List<CommitteeProfessor> assignments = new ArrayList<>();
        for (ProfessorAssignmentRequest request : professorRequests) {
            StaffUser professor = staffUserRepository.findById(request.getProfessorId())
                    .orElseThrow(() -> new NotFoundException("Professor not found: " + request.getProfessorId()));

            CommitteeProfessor assignment = new CommitteeProfessor();
            assignment.setCommittee(committee);
            assignment.setProfessor(professor);
            assignment.setRole(request.getRole());
            assignments.add(assignment);
        }

        committeeProfessorRepository.saveAll(assignments);
        return getCommitteeDetail(committeeId);
    }

    @Transactional
    public CommitteeDetailResponse assignGroups(UUID committeeId, List<UUID> groupIds) {
        Committee committee = committeeRepository.findDetailedById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found"));

        List<ProjectGroup> groups = projectGroupRepository.findAllById(groupIds);
        if (groups.size() != groupIds.size()) {
            throw new BusinessRuleException("One or more group ids are invalid");
        }

        for (ProjectGroup group : groups) {
            if (!committee.getTermId().equals(group.getTermId())) {
                throw new BusinessRuleException("Group term does not match the committee term");
            }

            if (group.getStatus() != ProjectGroup.GroupStatus.ADVISOR_ASSIGNED) {
                throw new BusinessRuleException("Group must complete Advisor Association before committee assignment");
            }

            if (group.getCommittee() != null && !group.getCommittee().getId().equals(committeeId)) {
                throw new BusinessRuleException("Group is already assigned to another committee");
            }
        }

        for (ProjectGroup group : groups) {
            group.setCommittee(committee);
        }
        projectGroupRepository.saveAll(groups);

        triggerAssignmentNotification(committeeId);
        return getCommitteeDetail(committeeId);
    }

    @Transactional(readOnly = true)
    public List<ProfessorCommitteeSummaryResponse> getCommitteesForProfessor(UUID professorId) {
        List<CommitteeProfessor> memberships = committeeProfessorRepository.findByProfessor_Id(professorId);

        return memberships.stream()
                .map(membership -> {
                    Committee committee = committeeRepository.findDetailedById(membership.getCommittee().getId())
                            .orElseThrow(() -> new NotFoundException("Committee not found"));

                    List<CommitteeGroupSummaryResponse> groups = committee.getGroups().stream()
                            .map(group -> new CommitteeGroupSummaryResponse(
                                    group.getId(),
                                    group.getGroupName(),
                                    group.getStatus().name()
                            ))
                            .collect(Collectors.toList());

                    return new ProfessorCommitteeSummaryResponse(
                            committee.getId(),
                            committee.getCommitteeName(),
                            membership.getRole(),
                            groups
                    );
                })
                .collect(Collectors.toList());
    }

    private void validateProfessorAssignments(Committee committee, List<ProfessorAssignmentRequest> professorRequests) {
        long advisorCount = professorRequests.stream()
                .filter(p -> p.getRole() == CommitteeProfessor.CommitteeRole.ADVISOR)
                .count();

        if (advisorCount != 1) {
            throw new BusinessRuleException("Exactly one ADVISOR must be assigned to a committee");
        }

        Set<UUID> seenProfessorIds = new HashSet<>();
        for (ProfessorAssignmentRequest request : professorRequests) {
            if (!seenProfessorIds.add(request.getProfessorId())) {
                throw new BusinessRuleException("Duplicate professor id in request: " + request.getProfessorId());
            }

            StaffUser professor = staffUserRepository.findById(request.getProfessorId())
                    .orElseThrow(() -> new NotFoundException("Professor not found: " + request.getProfessorId()));

            if (professor.getRole() != StaffUser.Role.Professor) {
                throw new BusinessRuleException("Only users with Professor role can be assigned to a committee");
            }

            boolean hasConflict = committeeProfessorRepository
                    .existsByProfessor_IdAndCommittee_TermIdAndCommittee_IdNot(
                            professor.getId(),
                            committee.getTermId(),
                            committee.getId()
                    );

            if (hasConflict) {
                throw new BusinessRuleException("Professor already belongs to another committee in the same term");
            }
        }
    }

    private void triggerAssignmentNotification(UUID committeeId) {
        Committee committee = committeeRepository.findDetailedById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found"));

        List<UUID> professorIds = committee.getProfessors().stream()
                .map(cp -> cp.getProfessor().getId())
                .distinct()
                .collect(Collectors.toList());

        List<CommitteeAssignmentNotificationPayload.AssignedGroupPayload> assignedGroups = committee.getGroups().stream()
                .map(group -> CommitteeAssignmentNotificationPayload.AssignedGroupPayload.builder()
                        .groupId(group.getId())
                        .groupName(group.getGroupName())
                        .build())
                .collect(Collectors.toList());

        CommitteeAssignmentNotificationPayload payload = CommitteeAssignmentNotificationPayload.builder()
                .committeeId(committee.getId())
                .professorIds(professorIds)
                .assignedGroups(assignedGroups)
                .build();

        committeeNotificationService.sendAssignmentNotification(payload);
    }

    private CommitteeDetailResponse toDetailResponse(Committee committee) {
        List<ProfessorAssignmentResponse> professors = committee.getProfessors().stream()
                .map(cp -> new ProfessorAssignmentResponse(
                        cp.getProfessor().getId(),
                        cp.getProfessor().getMail(),
                        cp.getRole()))
                .collect(Collectors.toList());

        List<UUID> groupIds = committee.getGroups().stream()
                .map(ProjectGroup::getId)
                .collect(Collectors.toList());

        return new CommitteeDetailResponse(
                committee.getId(),
                committee.getCommitteeName(),
                committee.getTermId(),
                professors,
                groupIds
        );
    }
}