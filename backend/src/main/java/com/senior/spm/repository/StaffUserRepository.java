package com.senior.spm.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.StaffUser;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    Optional<StaffUser> findByMail(String mail);
}
