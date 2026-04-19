package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

    boolean existsByStudentId(String studentId);

    Optional<Student> findByStudentId(String studentId);

    Optional<Student> findByGithubUsername(String githubUsername);

    @Query("""
        SELECT s FROM Student s
        LEFT JOIN s.memberships m
        WHERE m IS NULL
          AND s.studentId LIKE CONCAT('%', :q, '%')
    """)
    List<Student> findUngroupedByStudentIdContaining(@Param("q") String q);
}
