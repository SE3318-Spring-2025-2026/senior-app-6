package com.senior.spm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.AddGroupsToCommitteeRequest;
import com.senior.spm.controller.request.AddProfessorsToCommitteeRequest;
import com.senior.spm.controller.request.CreateCommitteeRequest;
import com.senior.spm.controller.response.CommitteeDetailResponse;
import com.senior.spm.controller.response.CommitteeGroupResponse;
import com.senior.spm.controller.response.CommitteeProfessorResponse;
import com.senior.spm.controller.response.CommitteeSummaryResponse;
import com.senior.spm.controller.response.ProfessorCommitteeDashboardResponse;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor;
import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.CommitteeProfessorRepository;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.service.dto.CommitteeAssignedGroupPayload;
import com.senior.spm.service.dto.CommitteeAssignmentNotificationPayload;
import com.senior.spm.service.event.CommitteeAssignmentNotificationsEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommitteeService {

    private final CommitteeRepository committeeRepository;
    private final CommitteeProfessorRepository committeeProfessorRepository;
    private final StaffUserRepository staffUserRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final DeliverableRepository deliverableRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final CommitteeValidationService committeeValidationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public CommitteeSummaryResponse createCommittee(CreateCommitteeRequest request) {
        if (committeeRepository.existsByCommitteeNameAndTermId(request.getCommitteeName(), request.getTermId())) {
            throw new AlreadyExistsException("Committee with this name already exists for the given term");
        }

        var deliverable = deliverableRepository.findById(request.getDeliverableId())
                .orElseThrow(() -> new NotFoundException("Deliverable not found: " + request.getDeliverableId()));

        var committee = new Committee();
        committee.setCommitteeName(request.getCommitteeName());
        committee.setTermId(request.getTermId());
        committee.setDeliverable(deliverable);

        var saved = committeeRepository.save(committee);
        return new CommitteeSummaryResponse(
                saved.getId(),
                saved.getCommitteeName(),
                saved.getTermId(),
                deliverable.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<CommitteeSummaryResponse> getCommittees(String termId) {
        var committees = (termId != null)
                ? committeeRepository.findByTermId(termId)
                : committeeRepository.findAll();

        return committees.stream()
                .map(c -> new CommitteeSummaryResponse(
                        c.getId(),
                        c.getCommitteeName(),
                        c.getTermId(),
                        c.getDeliverable().getId()))
                .toList();
    }

    @Transactional
    public List<CommitteeProfessorResponse> addProfessorsToCommittee(UUID committeeId, AddProfessorsToCommitteeRequest request) {
        var committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found: " + committeeId));

        var uniqueProfessorCount = request.getProfessors().stream()
                .map(p -> p.getProfessorId())
                .distinct()
                .count();

        if (uniqueProfessorCount != request.getProfessors().size()) {
            throw new IllegalArgumentException("Duplicate professor IDs are not allowed");
        }

        var advisorCount = request.getProfessors().stream()
                .filter(p -> p.getRole() == ProfessorRole.ADVISOR)
                .count();

        if (advisorCount != 1) {
            throw new IllegalArgumentException("Exactly one ADVISOR is required");
        }

        var deliverableId = committee.getDeliverable().getId();
        var responses = new ArrayList<CommitteeProfessorResponse>();

        for (var entry : request.getProfessors()) {
            var professor = staffUserRepository.findById(entry.getProfessorId())
                    .orElseThrow(() -> new NotFoundException("Professor not found: " + entry.getProfessorId()));

            committeeValidationService.validateProfessorNotAssignedToOtherCommittee(
                    entry.getProfessorId(), deliverableId);

            var committeeProfessor = new CommitteeProfessor();
            committeeProfessor.setCommittee(committee);
            committeeProfessor.setProfessor(professor);
            committeeProfessor.setRole(entry.getRole());

            committee.getProfessors().add(committeeProfessor);

            responses.add(new CommitteeProfessorResponse(
                    committeeProfessor.getId(),
                    professor.getId(),
                    professor.getMail(),
                    professor.getMail(),
                    committeeProfessor.getRole()
            ));
        }

        publishAssignmentNotificationsIfReady(committee);
        committeeRepository.save(committee);
        return responses;
    }

    @Transactional
    public List<CommitteeGroupResponse> addGroupsToCommittee(UUID committeeId, AddGroupsToCommitteeRequest request) {
        var committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found: " + committeeId));

        var deliverableId = committee.getDeliverable().getId();
        var responses = new ArrayList<CommitteeGroupResponse>();

        for (var groupId : request.getGroupIds()) {
            var group = projectGroupRepository.findById(groupId)
                    .orElseThrow(() -> new NotFoundException("Group not found: " + groupId));

            if (group.getStatus() != GroupStatus.ADVISOR_ASSIGNED) {
                throw new IllegalArgumentException("Group " + groupId + " has not completed advisor association");
            }

            committeeValidationService.validateGroupNotAssignedToOtherCommittee(groupId, deliverableId);

            committee.getGroups().add(group);

            responses.add(new CommitteeGroupResponse(
                    group.getId(),
                    group.getId(),
                    group.getGroupName(),
                    group.getStatus().name()
            ));
        }

        publishAssignmentNotificationsIfReady(committee);
        committeeRepository.save(committee);
        return responses;
    }

    @Transactional(readOnly = true)
    public CommitteeDetailResponse getCommitteeDetails(UUID id) {
        Committee committee = committeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Committee not found: " + id));

        List<CommitteeProfessorResponse> professorResponses = new ArrayList<>();
        for (CommitteeProfessor cp : committee.getProfessors()) {
            professorResponses.add(new CommitteeProfessorResponse(
                    cp.getId(),
                    cp.getProfessor().getId(),
                    cp.getProfessor().getMail(),
                    cp.getProfessor().getMail(),
                    cp.getRole()
            ));
        }

        List<CommitteeGroupResponse> groupResponses = new ArrayList<>();
        for (ProjectGroup group : committee.getGroups()) {
            groupResponses.add(new CommitteeGroupResponse(
                    group.getId(),
                    group.getId(),
                    group.getGroupName(),
                    group.getStatus().name()
            ));
        }

        return new CommitteeDetailResponse(
                committee.getId(),
                committee.getCommitteeName(),
                committee.getTermId(),
                committee.getDeliverable().getId(),
                professorResponses,
                groupResponses
        );
    }

    @Transactional(readOnly = true)
    public List<ProfessorCommitteeDashboardResponse> getCommitteesForProfessor(UUID professorId) {
        var assignments = committeeProfessorRepository.findByProfessorId(professorId);

        return assignments.stream()
                .map(assignment -> {
                    var committee = assignment.getCommittee();
                    var deliverable = committee.getDeliverable();

                    var groups = committee.getGroups().stream()
                            .map(group -> new ProfessorCommitteeDashboardResponse.GroupItem(
                                    group.getId(),
                                    group.getGroupName(),
                                    group.getStatus().name()
                            ))
                            .sorted((left, right) -> left.getGroupName().compareToIgnoreCase(right.getGroupName()))
                            .toList();

                    var rubrics = rubricCriterionRepository.findAllByDeliverableId(deliverable.getId()).stream()
                            .map(rubric -> new ProfessorCommitteeDashboardResponse.RubricItem(
                                    rubric.getCriterionName(),
                                    rubric.getGradingType(),
                                    rubric.getWeight()
                            ))
                            .sorted((left, right) -> left.getCriterionName().compareToIgnoreCase(right.getCriterionName()))
                            .toList();

                    return new ProfessorCommitteeDashboardResponse(
                            committee.getId(),
                            committee.getCommitteeName(),
                            committee.getTermId(),
                            assignment.getRole(),
                            deliverable.getId(),
                            deliverable.getName(),
                            deliverable.getType(),
                            deliverable.getSubmissionDeadline(),
                            deliverable.getReviewDeadline(),
                            deliverable.getWeight(),
                            groups,
                            rubrics
                    );
                })
                .sorted((left, right) -> left.getCommitteeName().compareToIgnoreCase(right.getCommitteeName()))
                .toList();
    }

    private void publishAssignmentNotificationsIfReady(Committee committee) {
        if (committee.getAssignmentNotificationSentAt() != null) {
            return;
        }

        if (committee.getProfessors() == null || committee.getProfessors().isEmpty()) {
            return;
        }

        if (committee.getGroups() == null || committee.getGroups().isEmpty()) {
            return;
        }

        List<CommitteeAssignedGroupPayload> assignedGroups = committee.getGroups().stream()
                .map(group -> new CommitteeAssignedGroupPayload(group.getId(), group.getGroupName()))
                .sorted(Comparator.comparing(CommitteeAssignedGroupPayload::groupName))
                .toList();

        List<CommitteeAssignmentNotificationPayload> notifications = committee.getProfessors().stream()
                .map(committeeProfessor -> new CommitteeAssignmentNotificationPayload(
                        committee.getId(),
                        committeeProfessor.getProfessor().getId(),
                        committeeProfessor.getProfessor().getMail(),
                        committeeProfessor.getRole(),
                        assignedGroups
                ))
                .sorted(Comparator.comparing(CommitteeAssignmentNotificationPayload::professorMail))
                .toList();

        applicationEventPublisher.publishEvent(
                new CommitteeAssignmentNotificationsEvent(committee.getId(), notifications)
        );
        committee.setAssignmentNotificationSentAt(LocalDateTime.now());
    }
}