package com.senior.spm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.senior.spm.entity.FinalGrade;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.Student;

@DataJpaTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class FinalGradeRepositoryTest extends RepositoryTestBase {

    @Autowired
    FinalGradeRepository repo;

    private FinalGrade makeFinalGrade(Student student, ProjectGroup group, String termId) {
        FinalGrade fg = new FinalGrade();
        fg.setStudent(student);
        fg.setGroup(group);
        fg.setTermId(termId);
        return em.persistAndFlush(fg);
    }

    // ── findByStudent_IdAndTermId ─────────────────────────────────────────────

    @Test
    void findByStudent_IdAndTermId_returnsGradeWhenExists() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        FinalGrade fg = makeFinalGrade(s, g, "2025-F");

        var result = repo.findByStudent_IdAndTermId(s.getId(), "2025-F");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(fg.getId());
    }

    @Test
    void findByStudent_IdAndTermId_returnsEmptyWhenNoGrade() {
        Student s = makeStudent("12345678901");

        var result = repo.findByStudent_IdAndTermId(s.getId(), "2025-F");

        assertThat(result).isEmpty();
    }

    @Test
    void findByStudent_IdAndTermId_doesNotMatchDifferentStudent() {
        Student s1 = makeStudent("12345678901");
        Student s2 = makeStudent("98765432100");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s1, g, "2025-F");

        var result = repo.findByStudent_IdAndTermId(s2.getId(), "2025-F");

        assertThat(result).isEmpty();
    }

    @Test
    void findByStudent_IdAndTermId_doesNotMatchDifferentTerm() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s, g, "2025-F");

        var result = repo.findByStudent_IdAndTermId(s.getId(), "2026-S");

        assertThat(result).isEmpty();
    }

    // ── findByStudent_StudentIdAndTermId ──────────────────────────────────────

    @Test
    void findByStudent_StudentIdAndTermId_returnsGradeWhenExists() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        FinalGrade fg = makeFinalGrade(s, g, "2025-F");

        var result = repo.findByStudent_StudentIdAndTermId("12345678901", "2025-F");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(fg.getId());
    }

    @Test
    void findByStudent_StudentIdAndTermId_returnsEmptyWhenNoGrade() {
        var result = repo.findByStudent_StudentIdAndTermId("99999999999", "2025-F");

        assertThat(result).isEmpty();
    }

    @Test
    void findByStudent_StudentIdAndTermId_doesNotMatchDifferentStudentId() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s, g, "2025-F");

        var result = repo.findByStudent_StudentIdAndTermId("99999999999", "2025-F");

        assertThat(result).isEmpty();
    }

    @Test
    void findByStudent_StudentIdAndTermId_doesNotMatchDifferentTerm() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s, g, "2025-F");

        var result = repo.findByStudent_StudentIdAndTermId("12345678901", "2026-S");

        assertThat(result).isEmpty();
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Test
    void uniqueConstraint_uqFgStudentTerm_preventsDuplicateStudentTermPair() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s, g, "2025-F");

        FinalGrade duplicate = new FinalGrade();
        duplicate.setStudent(s);
        duplicate.setGroup(g);
        duplicate.setTermId("2025-F");

        assertThatThrownBy(() -> em.persistAndFlush(duplicate))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void uniqueConstraint_uqFgStudentTerm_allowsSameStudentDifferentTerm() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s, g, "2025-F");
        makeFinalGrade(s, g, "2026-S");

        assertThat(repo.count()).isEqualTo(2);
    }
}
