package com.senior.spm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.response.DeliverableBreakdown;
import com.senior.spm.controller.response.FinalGradeResponse;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.FinalGrade;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ScrumGrade;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintDeliverableMapping;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.FinalGradeRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ScrumGradeRepository;
import com.senior.spm.repository.SprintDeliverableMappingRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service implementing Process 7.2 — Scalar &amp; Final Grade Calculation.
 *
 * <p>Algorithm (per docs/process7/endpoints_p7.md and docs/phase1_2.md Steps 3–11):
 * <ol>
 *   <li>Resolve Student by 11-digit studentId.</li>
 *   <li>Resolve GroupMembership → ProjectGroup.</li>
 *   <li>For each Deliverable: compute ScrumScalar, ReviewScalar, DS, B (stub 0.0), ScaledGrade, WeightedTotal.</li>
 *   <li>Compute C_i from SprintTrackingLog (assigneeGithubUsername + prMerged).</li>
 *   <li>Compute G_i = WeightedTotal × C_i.</li>
 *   <li>Upsert to FinalGrade table.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class FinalGradeCalculationService {

    private static final int SCALE = 6;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final StudentRepository studentRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final DeliverableRepository deliverableRepository;
    private final SprintRepository sprintRepository;
    private final SprintDeliverableMappingRepository sprintDeliverableMappingRepository;
    private final ScrumGradeRepository scrumGradeRepository;
    private final SprintTrackingLogRepository sprintTrackingLogRepository;
    private final FinalGradeRepository finalGradeRepository;
    private final TermConfigService termConfigService;

    /**
     * Calculates and upserts the final grade for the student identified by their
     * 11-digit student number, then returns the full result with a per-deliverable breakdown.
     *
     * @param studentId11Digit 11-digit student number (pattern ^[0-9]{11}$)
     * @return FinalGradeResponse with breakdown and upserted grade values
     * @throws NotFoundException if student not found or student has no group membership
     */
    @Transactional
    public FinalGradeResponse calculateFinalGrade(String studentId11Digit) {

        // Step 1 — Resolve student
        Student student = studentRepository.findByStudentId(studentId11Digit)
                .orElseThrow(() -> new NotFoundException("Student not found"));

        // Step 2 — Resolve group membership
        GroupMembership membership = groupMembershipRepository.findByStudentId(student.getId())
                .orElseThrow(() -> new NotFoundException("Student has no group membership"));

        var group = membership.getGroup();
        String termId = termConfigService.getActiveTermId();

        // Step 3 — Fetch all deliverables and all sprints for the term
        List<Deliverable> deliverables = deliverableRepository.findAll();
        List<Sprint> allSprints = sprintRepository.findAll();

        List<DeliverableBreakdown> breakdowns = new ArrayList<>();
        BigDecimal weightedTotal = BigDecimal.ZERO;

        for (Deliverable deliverable : deliverables) {

            // a. Contributing sprints for this deliverable
            List<SprintDeliverableMapping> mappings =
                    sprintDeliverableMappingRepository.findAllByDeliverableId(deliverable.getId());

            // b & c. Collect pointA and pointB numeric values across contributing sprints.
            // Skip sprints with no ScrumGrade for this group (edge case D4).
            List<Integer> pointAValues = new ArrayList<>();
            List<Integer> pointBValues = new ArrayList<>();

            for (SprintDeliverableMapping mapping : mappings) {
                Sprint sprint = mapping.getSprint();
                Optional<ScrumGrade> gradeOpt =
                        scrumGradeRepository.findByGroupIdAndSprintId(group.getId(), sprint.getId());

                gradeOpt.ifPresent(grade -> {
                    pointAValues.add(grade.getPointAGrade().toNumeric());
                    pointBValues.add(grade.getPointBGrade().toNumeric());
                });
            }

            // ScrumScalar = AVG(pointAValues) / 100  (plain average — see D1 in endpoints_p7.md)
            // TODO: consider weighted average using contributionPercentage if product owner requires it
            BigDecimal scrumScalar = computeAverage(pointAValues)
                    .divide(BigDecimal.valueOf(100), SCALE, RM);

            // ReviewScalar = AVG(pointBValues) / 100
            BigDecimal reviewScalar = computeAverage(pointBValues)
                    .divide(BigDecimal.valueOf(100), SCALE, RM);

            // DS = (ScrumScalar + ReviewScalar) / 2
            BigDecimal ds = scrumScalar.add(reviewScalar)
                    .divide(BigDecimal.valueOf(2), SCALE, RM);

            // B = 0.0 — stub until Issue #249 (RubricGrade) ships
            BigDecimal b = BigDecimal.ZERO;

            // ScaledGrade = B × DS
            BigDecimal scaledGrade = b.multiply(ds).setScale(SCALE, RM);

            // weightedContribution = ScaledGrade × (deliverable.weight / 100)
            BigDecimal weight = deliverable.getWeight() != null
                    ? deliverable.getWeight()
                    : BigDecimal.ZERO;
            BigDecimal weightedContribution = scaledGrade
                    .multiply(weight)
                    .divide(BigDecimal.valueOf(100), SCALE, RM);

            weightedTotal = weightedTotal.add(weightedContribution);

            breakdowns.add(DeliverableBreakdown.builder()
                    .deliverableId(deliverable.getId())
                    .deliverableName(deliverable.getName())
                    .baseGrade(b)
                    .scrumScalar(scrumScalar)
                    .reviewScalar(reviewScalar)
                    .deliverableScalar(ds)
                    .scaledGrade(scaledGrade)
                    .weight(weight)
                    .weightedContribution(weightedContribution)
                    .build());
        }

        // Step 4 — C_i: Individual Completion Ratio
        // Filter group's SprintTrackingLog: assigneeGithubUsername == student.githubUsername AND prMerged == true
        List<SprintTrackingLog> groupLogs = sprintTrackingLogRepository.findByGroupId(group.getId());

        String studentGithubUsername = student.getGithubUsername();

        int completedStoryPoints = groupLogs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getPrMerged()))
                .filter(log -> studentGithubUsername != null
                        && studentGithubUsername.equals(log.getAssigneeGithubUsername()))
                .mapToInt(log -> log.getStoryPoints() != null ? log.getStoryPoints() : 0)
                .sum();

        int targetStoryPoints = allSprints.stream()
                .mapToInt(sprint -> sprint.getStoryPointTarget() != null ? sprint.getStoryPointTarget() : 0)
                .sum();

        // C_i = completedStoryPoints / targetStoryPoints (not capped at 1.0 — see D2)
        BigDecimal completionRatio;
        if (targetStoryPoints == 0) {
            completionRatio = BigDecimal.ZERO; // avoid division by zero (edge case D4)
        } else {
            completionRatio = BigDecimal.valueOf(completedStoryPoints)
                    .divide(BigDecimal.valueOf(targetStoryPoints), SCALE, RM);
        }

        // Step 5 — G_i = WeightedTotal × C_i
        BigDecimal finalGrade = weightedTotal.multiply(completionRatio).setScale(SCALE, RM);

        LocalDateTime calculatedAt = LocalDateTime.now();

        // Step 6 — Upsert to FinalGrade table
        FinalGrade entity = finalGradeRepository
                .findByStudent_StudentIdAndTermId(studentId11Digit, termId)
                .orElseGet(FinalGrade::new);

        entity.setStudent(student);
        entity.setGroup(group);
        entity.setTermId(termId);
        entity.setWeightedTotal(weightedTotal.setScale(4, RM));
        entity.setCompletionRatio(completionRatio.setScale(4, RM));
        entity.setFinalGrade(finalGrade.setScale(4, RM));
        entity.setCalculatedAt(calculatedAt);
        finalGradeRepository.save(entity);

        return FinalGradeResponse.builder()
                .studentId(studentId11Digit)
                .groupId(group.getId())
                .deliverableBreakdown(breakdowns)
                .weightedTotal(weightedTotal.setScale(4, RM))
                .completionRatio(completionRatio.setScale(4, RM))
                .finalGrade(finalGrade.setScale(4, RM))
                .calculatedAt(calculatedAt)
                .build();
    }

    /**
     * Returns the plain average of the given integer list as a BigDecimal.
     * Returns BigDecimal.ZERO if the list is empty (handles the no-ScrumGrade edge case).
     */
    private BigDecimal computeAverage(List<Integer> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int sum = values.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(values.size()), SCALE, RM);
    }
}
