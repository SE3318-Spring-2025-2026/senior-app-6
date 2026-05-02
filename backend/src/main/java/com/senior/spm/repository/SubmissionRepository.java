package com.senior.spm.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.Submission;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
}