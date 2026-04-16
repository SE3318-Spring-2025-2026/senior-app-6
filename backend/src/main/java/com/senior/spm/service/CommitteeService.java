package com.senior.spm.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.AddGroupToCommitteeRequest;
import com.senior.spm.controller.request.AddProfessorToCommitteeRequest;
import com.senior.spm.controller.response.CommitteeGroupResponse;
import com.senior.spm.controller.response.CommitteeProfessorResponse;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.StaffUser;
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

    /**
     * Adds a professor to a committee with validation.
     *
     * @param committeeId the committee ID
     * @param request the request containing professorId and role
     * @return the created CommitteeProfessorResponse
     * @throws NotFoundException if committee or professor not found
     * @throws ConflictException if professor is already assigned to another committee for same deliverable
     */
    @Transactional
    public CommitteeProfessorResponse addProfessorToCommittee(UUID committeeId, AddProfessorToCommitteeRequest request) {
        Committee committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found: " + committeeId));

        StaffUser professor = staffUserRepository.findById(request.getProfessorId())
                .orElseThrow(() -> new NotFoundException("Professor not found: " + request.getProfessorId()));

        // Validate no scheduling conflict
        committeeValidationService.validateProfessorNotAssignedToOtherCommittee(
                request.getProfessorId(), committee.getDeliverable().getId());

        // Create and save CommitteeProfessor
        CommitteeProfessor committeeProfessor = new CommitteeProfessor();
        committeeProfessor.setCommittee(committee);
        committeeProfessor.setProfessor(professor);
        committeeProfessor.setRole(request.getRole());

        committee.getProfessors().add(committeeProfessor);
        committeeRepository.save(committee);

        return new CommitteeProfessorResponse(
                committeeProfessor.getId(),
                professor.getId(),
                professor.getMail(), // StaffUser has no name field, using mail as identifier
                professor.getMail(),
                committeeProfessor.getRole()
        );
    }

    /**
     * Adds a group to a committee with validation.
     *
     * @param committeeId the committee ID
     * @param request the request containing groupId
     * @return the created CommitteeGroupResponse
     * @throws NotFoundException if committee or group not found
     * @throws ConflictException if group is already assigned to another committee for same deliverable
     */
    @Transactional
    public CommitteeGroupResponse addGroupToCommittee(UUID committeeId, AddGroupToCommitteeRequest request) {
        Committee committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found: " + committeeId));

        ProjectGroup group = projectGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new NotFoundException("Group not found: " + request.getGroupId()));

        // Validate no duplicate assignment
        committeeValidationService.validateGroupNotAssignedToOtherCommittee(
                request.getGroupId(), committee.getDeliverable().getId());

        // Add group to committee
        committee.getGroups().add(group);
        committeeRepository.save(committee);

        return new CommitteeGroupResponse(
                group.getId(),
                group.getId(),
                group.getGroupName(),
                group.getStatus().name()
        );
    }
}