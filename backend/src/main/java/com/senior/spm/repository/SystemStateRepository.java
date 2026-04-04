package com.senior.spm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.SystemState;

@Repository
public interface SystemStateRepository extends JpaRepository<SystemState, Long> {
}
