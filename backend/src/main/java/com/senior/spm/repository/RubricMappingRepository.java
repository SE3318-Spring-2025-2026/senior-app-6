package com.senior.spm.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.RubricMapping;

@Repository
public interface RubricMappingRepository extends JpaRepository<RubricMapping, UUID> {

    List<RubricMapping> findBySubmissionId(UUID submissionId);

    void deleteBySubmissionId(UUID submissionId);
}
