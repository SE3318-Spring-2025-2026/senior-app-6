package com.senior.spm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.SubmitGradesRequest;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.RubricGrade;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.RubricGradeRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.util.GradeValueMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RubricGradingService {

    private final DeliverableSubmissionRepository submissionRepository;
    private final CommitteeRepository committeeRepository;
    private final RubricCriterionRepository criterionRepository;
    private final RubricGradeRepository rubricGradeRepository;
    private final StaffUserRepository staffUserRepository;

    /**
     * Submits or updates rubric grades for a submission.
     * Returns a result containing the base deliverable grade and whether this
     * is the reviewer's first grade (used by the controller for 201 vs 200).
     */
    @Transactional
    public RubricGradingResult submitGrades(
            UUID submissionId,
            UUID reviewerStaffUserId,
            List<SubmitGradesRequest.GradeEntry> grades) {

        // Step 1: Resolve submission → get deliverableId + groupId
        DeliverableSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));

        UUID deliverableId = submission.getDeliverable().getId();
        UUID groupId = submission.getGroup().getId();

        // Step 2: Committee auth check — deliverable-scoped.
        // A professor on the Proposal committee cannot grade the SoW submission.
        if (!committeeRepository.existsByProfessorIdAndGroupIdAndDeliverableId(
                reviewerStaffUserId, groupId, deliverableId)) {
            throw new ForbiddenException("You are not a committee member for this submission");
        }

        // Step 3: Fetch rubric criteria for this deliverable and validate payload completeness
        List<RubricCriterion> allCriteria = criterionRepository.findAllByDeliverableId(deliverableId);

        if (allCriteria.isEmpty()) {
            throw new BusinessRuleException("No rubric criteria configured for this deliverable");
        }

        Map<UUID, RubricCriterion> criterionMap = allCriteria.stream()
                .collect(Collectors.toMap(RubricCriterion::getId, c -> c));

        Set<UUID> payloadCriterionIds = grades.stream()
                .map(SubmitGradesRequest.GradeEntry::getCriterionId)
                .collect(Collectors.toSet());

        // Every criterion in the rubric must be present in the payload
        for (RubricCriterion criterion : allCriteria) {
            if (!payloadCriterionIds.contains(criterion.getId())) {
                throw new BusinessRuleException("Missing grade for criterion " + criterion.getId());
            }
        }

        // No extra/unknown criterion IDs allowed
        for (UUID payloadId : payloadCriterionIds) {
            if (!criterionMap.containsKey(payloadId)) {
                throw new BusinessRuleException("Unknown criterion " + payloadId + " for this deliverable");
            }
        }

        // Step 4: Validate each grade value against its criterion type
        for (SubmitGradesRequest.GradeEntry entry : grades) {
            RubricCriterion criterion = criterionMap.get(entry.getCriterionId());
            if (!GradeValueMapper.validateGrade(criterion.getGradingType(), entry.getSelectedGrade())) {
                throw new BusinessRuleException(
                        "Invalid grade '" + entry.getSelectedGrade() + "' for "
                                + criterion.getGradingType() + " criterion " + entry.getCriterionId());
            }
        }

        // Step 5: Determine first-grade status BEFORE any upsert
        List<RubricGrade> existingBeforeUpsert = rubricGradeRepository.findBySubmissionId(submissionId);
        boolean isFirstGrade = existingBeforeUpsert.stream()
                .noneMatch(g -> g.getReviewer().getId().equals(reviewerStaffUserId));

        // Step 6: Fetch reviewer entity (guaranteed to exist — principal from valid JWT)
        StaffUser reviewer = staffUserRepository.findById(reviewerStaffUserId)
                .orElseThrow(() -> new NotFoundException("Reviewer not found"));

        // Step 7: Upsert one RubricGrade row per criterion
        for (SubmitGradesRequest.GradeEntry entry : grades) {
            RubricCriterion criterion = criterionMap.get(entry.getCriterionId());

            Optional<RubricGrade> existing = rubricGradeRepository
                    .findBySubmissionIdAndCriterionIdAndReviewerId(
                            submissionId, entry.getCriterionId(), reviewerStaffUserId);

            if (existing.isPresent()) {
                RubricGrade grade = existing.get();
                grade.setSelectedGrade(entry.getSelectedGrade());
                grade.setGradedAt(LocalDateTime.now());
                rubricGradeRepository.save(grade);
            } else {
                RubricGrade grade = new RubricGrade();
                grade.setSubmission(submission);
                grade.setCriterion(criterion);
                grade.setReviewer(reviewer);
                grade.setSelectedGrade(entry.getSelectedGrade());
                grade.setGradedAt(LocalDateTime.now());
                rubricGradeRepository.save(grade);
            }
        }

        // Step 8: Compute base deliverable grade across all reviewers
        //   B = AVG_reviewers( SUM_criteria(numericGrade × weight) / SUM(weight) )
        List<RubricGrade> allGradesAfterUpsert = rubricGradeRepository.findBySubmissionId(submissionId);
        double baseDeliverableGrade = computeBaseDeliverableGrade(allGradesAfterUpsert);

        return new RubricGradingResult(submissionId, reviewerStaffUserId, baseDeliverableGrade, isFirstGrade);
    }

    private double computeBaseDeliverableGrade(List<RubricGrade> allGrades) {
        if (allGrades.isEmpty()) {
            return 0.0;
        }

        Map<UUID, List<RubricGrade>> byReviewer = allGrades.stream()
                .collect(Collectors.groupingBy(g -> g.getReviewer().getId()));

        return byReviewer.values().stream()
                .mapToDouble(reviewerGrades -> {
                    double weightedSum = reviewerGrades.stream()
                            .mapToDouble(g -> GradeValueMapper.toNumeric(
                                    g.getCriterion().getGradingType(),
                                    g.getSelectedGrade())
                                    * g.getCriterion().getWeight().doubleValue())
                            .sum();
                    double totalWeight = reviewerGrades.stream()
                            .mapToDouble(g -> g.getCriterion().getWeight().doubleValue())
                            .sum();
                    return totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
                })
                .average()
                .orElse(0.0);
    }

    public record RubricGradingResult(
            UUID submissionId,
            UUID reviewerId,
            double baseDeliverableGrade,
            boolean isFirstGrade) {
    }
}
