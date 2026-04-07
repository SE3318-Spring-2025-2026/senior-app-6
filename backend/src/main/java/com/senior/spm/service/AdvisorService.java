package com.senior.spm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.exception.AdvisorAtCapacityException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.RequestNotFoundException;
import com.senior.spm.exception.RequestNotPendingException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.service.dto.AdvisorRequestDetail;
import com.senior.spm.service.dto.AdvisorRequestSummary;
import com.senior.spm.service.dto.AdvisorRespondResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdvisorService {

    private final AdvisorRequestRepository advisorRequestRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final TermConfigService termConfigService;

    @Transactional(readOnly = true)
    public List<AdvisorRequestSummary> getPendingRequestsForAdvisor(UUID professorId) {
        List<AdvisorRequest> requests = advisorRequestRepository.findByAdvisorIdAndStatus(professorId, AdvisorRequest.RequestStatus.PENDING);
        
        return requests.stream().map(req -> 
            AdvisorRequestSummary.builder()
                .requestId(req.getId())
                .groupId(req.getGroup().getId())
                .groupName(req.getGroup().getGroupName())
                .termId(req.getGroup().getTermId())
                .memberCount((int) groupMembershipRepository.countByGroupId(req.getGroup().getId()))
                .sentAt(req.getSentAt())
                .build()
        ).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdvisorRequestDetail getRequestDetail(UUID requestId, UUID professorId) {
        AdvisorRequest request = advisorRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("Request not found"));
            
        if (!request.getAdvisor().getId().equals(professorId)) {
            throw new ForbiddenException("This request is not addressed to you");
        }

        ProjectGroup group = request.getGroup();
        
        var members = group.getMembers().stream()
            .map(m -> AdvisorRequestDetail.GroupMemberDetail.builder()
                .studentId(m.getStudent().getStudentId())
                .role(m.getRole().name())
                .joinedAt(m.getJoinedAt())
                .build()
            ).collect(Collectors.toList());

        var groupDetail = AdvisorRequestDetail.RequestGroupDetail.builder()
            .id(group.getId())
            .groupName(group.getGroupName())
            .termId(group.getTermId())
            .status(group.getStatus())
            .members(members)
            .build();

        return AdvisorRequestDetail.builder()
            .requestId(request.getId())
            .group(groupDetail)
            .sentAt(request.getSentAt())
            .build();
    }

    @Transactional
    public AdvisorRespondResponse respondToRequest(UUID requestId, UUID professorId, boolean accept) {
        AdvisorRequest request = advisorRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("Request not found"));

        if (!request.getAdvisor().getId().equals(professorId)) {
            throw new ForbiddenException("This request is not addressed to you");
        }

        if (request.getStatus() != AdvisorRequest.RequestStatus.PENDING) {
            throw new RequestNotPendingException("Request is no longer pending");
        }
        
        LocalDateTime now = LocalDateTime.now();

        if (!accept) {
            request.setStatus(AdvisorRequest.RequestStatus.REJECTED);
            request.setRespondedAt(now);
            advisorRequestRepository.save(request);
            
            return AdvisorRespondResponse.builder()
                .requestId(request.getId())
                .status(AdvisorRequest.RequestStatus.REJECTED)
                .build();
        }

        // Accept Flow
        String activeTermId = termConfigService.getActiveTermId();
        
        long currentGroupCount = projectGroupRepository.countByAdvisorIdAndTermIdAndStatusNot(
                professorId, activeTermId, ProjectGroup.GroupStatus.DISBANDED);
                
        if (currentGroupCount >= request.getAdvisor().getAdvisorCapacity()) {
            throw new AdvisorAtCapacityException("You have reached your maximum group capacity for this term");
        }

        // Proceed to accept
        request.setStatus(AdvisorRequest.RequestStatus.ACCEPTED);
        request.setRespondedAt(now);
        advisorRequestRepository.save(request);

        ProjectGroup group = request.getGroup();
        
        if (group.getStatus() != ProjectGroup.GroupStatus.TOOLS_BOUND) {
            throw new RequestNotPendingException("Group is no longer in TOOLS_BOUND status");
        }
        
        group.setAdvisor(request.getAdvisor());
        group.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        projectGroupRepository.save(group);

        // Auto-reject other pending requests for this group
        advisorRequestRepository.bulkUpdateStatusForGroup(AdvisorRequest.RequestStatus.AUTO_REJECTED, group.getId(), request.getId());

        return AdvisorRespondResponse.builder()
            .requestId(request.getId())
            .status(AdvisorRequest.RequestStatus.ACCEPTED)
            .groupId(group.getId())
            .groupStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED)
            .build();
    }
}