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

    private FinalGrade makeFinalGrade(Student student, ProjectGroup group) {
        FinalGrade fg = new FinalGrade();
        fg.setStudent(student);
        fg.setGroup(group);
        return em.persistAndFlush(fg);
    }

    // ── findByStudent_Id ──────────────────────────────────────────────────────

    @Test
    void findByStudent_Id_returnsGradeWhenExists() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        FinalGrade fg = makeFinalGrade(s, g);

        var result = repo.findByStudent_Id(s.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(fg.getId());
    }

    @Test
    void findByStudent_Id_returnsEmptyWhenNoGrade() {
        Student s = makeStudent("12345678901");

        var result = repo.findByStudent_Id(s.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByStudent_Id_doesNotMatchDifferentStudent() {
        Student s1 = makeStudent("12345678901");
        Student s2 = makeStudent("98765432100");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s1, g);

        var result = repo.findByStudent_Id(s2.getId());

        assertThat(result).isEmpty();
    }

    // ── findByStudent_StudentId ───────────────────────────────────────────────

    @Test
    void findByStudent_StudentId_returnsGradeWhenExists() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        FinalGrade fg = makeFinalGrade(s, g);

        var result = repo.findByStudent_StudentId("12345678901");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(fg.getId());
    }

    @Test
    void findByStudent_StudentId_returnsEmptyWhenNoGrade() {
        var result = repo.findByStudent_StudentId("99999999999");

        assertThat(result).isEmpty();
    }

    @Test
    void findByStudent_StudentId_doesNotMatchDifferentStudentId() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s, g);

        var result = repo.findByStudent_StudentId("99999999999");

        assertThat(result).isEmpty();
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Test
    void uniqueConstraint_uqFgStudent_preventsSecondGradeForSameStudent() {
        Student s = makeStudent("12345678901");
        ProjectGroup g = makeGroup("G1", "T1", ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        makeFinalGrade(s, g);

        FinalGrade duplicate = new FinalGrade();
        duplicate.setStudent(s);
        duplicate.setGroup(g);

        assertThatThrownBy(() -> em.persistAndFlush(duplicate))
                .isInstanceOf(PersistenceException.class);
    }
}