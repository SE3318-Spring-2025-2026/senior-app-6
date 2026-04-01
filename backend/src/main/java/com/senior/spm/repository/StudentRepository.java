package com.senior.spm.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

    boolean existsByStudentId(String studentId);
}
