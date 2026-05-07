package com.senior.spm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final int SCALE = 6;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final DeliverableSubmissionRepository submissionRepository;
    private final CommitteeRepository committeeRepository;
    private final RubricCriterionRepository criterionRepository;
    private final RubricGradeRepository rubricGradeRepository;
    private final StaffUserRepository staffUserRepository;

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
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        for (SubmitGradesRequest.GradeEntry entry : grades) {
            RubricCriterion criterion = criterionMap.get(entry.getCriterionId());

            Optional<RubricGrade> existing = rubricGradeRepository
                    .findBySubmissionIdAndCriterionIdAndReviewerId(
                            submissionId, entry.getCriterionId(), reviewerStaffUserId);

            if (existing.isPresent()) {
                RubricGrade grade = existing.get();
                grade.setSelectedGrade(entry.getSelectedGrade());
                grade.setGradedAt(now);
                rubricGradeRepository.save(grade);
            } else {
                RubricGrade grade = new RubricGrade();
                grade.setSubmission(submission);
                grade.setCriterion(criterion);
                grade.setReviewer(reviewer);
                grade.setSelectedGrade(entry.getSelectedGrade());
                grade.setGradedAt(now);
                rubricGradeRepository.save(grade);
            }
        }

        // Step 8: Compute B = AVG_reviewers( SUM_criteria(numericGrade × weight) / SUM(weight) )
        List<RubricGrade> allGradesAfterUpsert = rubricGradeRepository.findBySubmissionId(submissionId);
        BigDecimal baseDeliverableGrade = computeBaseDeliverableGrade(allGradesAfterUpsert);

        return new RubricGradingResult(baseDeliverableGrade, isFirstGrade);
    }

    private BigDecimal computeBaseDeliverableGrade(List<RubricGrade> allGrades) {
        if (allGrades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<UUID, List<RubricGrade>> byReviewer = allGrades.stream()
                .collect(Collectors.groupingBy(g -> g.getReviewer().getId()));

        BigDecimal reviewerSum = BigDecimal.ZERO;
        for (List<RubricGrade> reviewerGrades : byReviewer.values()) {
            BigDecimal numerator = BigDecimal.ZERO;
            BigDecimal denominator = BigDecimal.ZERO;
            for (RubricGrade g : reviewerGrades) {
                int numeric = GradeValueMapper.toNumeric(g.getCriterion().getGradingType(), g.getSelectedGrade());
                numerator = numerator.add(BigDecimal.valueOf(numeric).multiply(g.getCriterion().getWeight()));
                denominator = denominator.add(g.getCriterion().getWeight());
            }
            if (denominator.compareTo(BigDecimal.ZERO) == 0) continue;
            reviewerSum = reviewerSum.add(numerator.divide(denominator, SCALE, RM));
        }

        return reviewerSum.divide(BigDecimal.valueOf(byReviewer.size()), SCALE, RM);
    }

    public record RubricGradingResult(BigDecimal baseDeliverableGrade, boolean isFirstGrade) {}
}
