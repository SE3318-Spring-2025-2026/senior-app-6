package com.senior.spm.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.FinalGrade;

@Repository
public interface FinalGradeRepository extends JpaRepository<FinalGrade, UUID> {

    Optional<FinalGrade> findByStudent_Id(UUID studentId);

    Optional<FinalGrade> findByStudent_StudentId(String studentNumber);
}
