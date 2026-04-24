package com.senior.spm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.Sprint;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID> {

    List<Sprint> findByEndDate(LocalDate endDate);

    List<Sprint> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate startDate, LocalDate endDate);
}
