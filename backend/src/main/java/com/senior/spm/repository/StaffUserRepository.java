package com.senior.spm.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    Optional<StaffUser> findByMail(String mail);

    List<StaffUser> findByRole(Role role);
}
