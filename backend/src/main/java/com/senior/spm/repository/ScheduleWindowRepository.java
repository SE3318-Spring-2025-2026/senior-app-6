package com.senior.spm.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;

@Repository
public interface ScheduleWindowRepository extends JpaRepository<ScheduleWindow, UUID> {

    Optional<ScheduleWindow> findByTermIdAndType(UUID termId, WindowType type);

    List<ScheduleWindow> findByTypeAndClosesAtLessThan(WindowType type, LocalDateTime dateTime);
}
