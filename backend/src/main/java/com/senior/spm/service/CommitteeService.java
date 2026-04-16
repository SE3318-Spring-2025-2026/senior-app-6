package com.senior.spm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.AddGroupsToCommitteeRequest;
import com.senior.spm.controller.request.AddProfessorsToCommitteeRequest;
import com.senior.spm.controller.response.CommitteeDetailResponse;
import com.senior.spm.controller.response.CommitteeGroupResponse;
import com.senior.spm.controller.response.CommitteeProfessorResponse;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor;
import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommitteeService {

    private final CommitteeRepository committeeRepository;
    private final StaffUserRepository staffUserRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final CommitteeValidationService committeeValidationService;

    @Transactional
    public List<CommitteeProfessorResponse> addProfessorsToCommittee(UUID committeeId, AddProfessorsToCommitteeRequest request) {
        var committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found: " + committeeId));

        var advisorCount = request.getProfessors().stream()
                .filter(p -> p.getRole() == ProfessorRole.ADVISOR).count();
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
                    committeeProfessor.getId(), professor.getId(), professor.getMail(), professor.getMail(), committeeProfessor.getRole()));
        }

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
                    group.getId(), group.getId(), group.getGroupName(), group.getStatus().name()));
        }

        committeeRepository.save(committee);
        return responses;
    }

    /**
     * Fetches detailed information for a specific committee, including assigned
     * professors and bounded student groups.
     *
     * @param id the committee ID
     * @return the fully populated CommitteeDetailResponse
     * @throws NotFoundException if the committee does not exist
     */
    @Transactional(readOnly = true)
    public CommitteeDetailResponse getCommitteeDetails(UUID id) {
        Committee committee = committeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Committee not found: " + id));

        // Map professors
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

        // Map groups
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
}
