package com.senior.spm.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.FinalGrade;

@Repository
public interface FinalGradeRepository extends JpaRepository<FinalGrade, UUID> {

    Optional<FinalGrade> findByStudent_IdAndTermId(UUID studentId, String termId);

    Optional<FinalGrade> findByStudent_StudentIdAndTermId(String studentNumber, String termId);
}
