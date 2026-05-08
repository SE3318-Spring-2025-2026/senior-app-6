package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.senior.spm.entity.AuditLog;
import com.senior.spm.entity.AuditLog.Category;
import com.senior.spm.entity.AuditLog.Outcome;
import com.senior.spm.entity.AuditLog.UserType;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies AuditLogRepository.search() — the 6-param optional-filter paginated query.
 *
 * Acceptance criteria (Issue #300):
 *   - All 6 filter params accept null (no filter applied when null)
 *   - Results paginated and sorted by occurredAt DESC
 *   - search(null,null,null,null,null,null,PageRequest.of(0,20)) returns all rows
 *   - search(null,null,FAILURE,null,null,null,...) returns only FAILURE rows
 */
@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class AuditLogRepositoryTest extends RepositoryTestBase {

    @Autowired
    AuditLogRepository repo;

    private final UUID userId1 = UUID.randomUUID();
    private final UUID userId2 = UUID.randomUUID();

    /** Persists a minimal AuditLog directly via TestEntityManager. */
    private AuditLog makeAuditLog(UUID userId, UserType userType, String action,
                                   Category category, Outcome outcome, LocalDateTime occurredAt) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUserType(userType);
        log.setAction(action);
        log.setCategory(category);
        log.setOutcome(outcome);
        log.setOccurredAt(occurredAt);
        return em.persistAndFlush(log);
    }

    @BeforeEach
    void seed() {
        // Row 1 — oldest
        makeAuditLog(userId1, UserType.STUDENT, "STUDENT_LOGIN",
                Category.AUTH, Outcome.SUCCESS,
                LocalDateTime.now().minusHours(3));

        // Row 2
        makeAuditLog(userId1, UserType.STAFF, "STAFF_LOGIN",
                Category.AUTH, Outcome.FAILURE,
                LocalDateTime.now().minusHours(2));

        // Row 3
        makeAuditLog(userId2, UserType.STAFF, "GROUP_CREATED",
                Category.GROUP, Outcome.SUCCESS,
                LocalDateTime.now().minusHours(1));

        // Row 4 — most recent
        makeAuditLog(userId2, UserType.STUDENT, "ADVISOR_REQUEST_SENT",
                Category.ADVISOR, Outcome.SUCCESS,
                LocalDateTime.now());
    }

    // ── Acceptance Criteria (Issue #300) ─────────────────────────────────────

    @Test
    void search_noFilters_returnsAllRows() {
        Page<AuditLog> result = repo.search(null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    void search_filterByOutcome_returnsOnlyFailureRows() {
        Page<AuditLog> result = repo.search(null, null, Outcome.FAILURE, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).allMatch(a -> a.getOutcome() == Outcome.FAILURE);
    }

    // ── Additional robustness tests ───────────────────────────────────────────

    @Test
    void search_filterByCategory_returnsOnlyMatchingRows() {
        Page<AuditLog> result = repo.search(null, Category.AUTH, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(a -> a.getCategory() == Category.AUTH);
    }

    @Test
    void search_filterByUserType_returnsOnlyMatchingRows() {
        Page<AuditLog> result = repo.search(UserType.STAFF, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(a -> a.getUserType() == UserType.STAFF);
    }

    @Test
    void search_filterByUserId_returnsOnlyMatchingRows() {
        Page<AuditLog> result = repo.search(null, null, null, userId1, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(a -> userId1.equals(a.getUserId()));
    }

    @Test
    void search_filterByDateRange_returnsOnlyInRangeRows() {
        LocalDateTime from = LocalDateTime.now().minusHours(2).minusMinutes(30);
        LocalDateTime to   = LocalDateTime.now().minusMinutes(30);

        Page<AuditLog> result = repo.search(null, null, null, null, from, to,
                PageRequest.of(0, 20));

        // Only rows 2 (minus 2h) and 3 (minus 1h) fall in this range
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(a -> !a.getOccurredAt().isBefore(from) && !a.getOccurredAt().isAfter(to));
    }

    @Test
    void search_resultsOrderedByOccurredAtDesc() {
        Page<AuditLog> result = repo.search(null, null, null, null, null, null,
                PageRequest.of(0, 20));

        var items = result.getContent();
        for (int i = 0; i < items.size() - 1; i++) {
            assertThat(items.get(i).getOccurredAt())
                    .isAfterOrEqualTo(items.get(i + 1).getOccurredAt());
        }
    }

    @Test
    void search_pagination_respectsPageSizeAndOffset() {
        // Page 0 with size 2 → first 2 (most recent)
        Page<AuditLog> page0 = repo.search(null, null, null, null, null, null,
                PageRequest.of(0, 2));
        // Page 1 with size 2 → remaining 2
        Page<AuditLog> page1 = repo.search(null, null, null, null, null, null,
                PageRequest.of(1, 2));

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.getTotalElements()).isEqualTo(4);

        // No overlap between pages
        var page0Ids = page0.getContent().stream().map(AuditLog::getId).toList();
        var page1Ids = page1.getContent().stream().map(AuditLog::getId).toList();
        assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
    }

    @Test
    void search_emptyTable_returnsEmptyPage() {
        // Delete all seeded rows
        repo.deleteAll();
        em.flush();

        Page<AuditLog> result = repo.search(null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getContent()).isEmpty();
    }
}
