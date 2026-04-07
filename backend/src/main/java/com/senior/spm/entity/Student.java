package com.senior.spm.entity;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 11, nullable = false, unique = true)
    @Pattern(regexp = "^[0-9]{11}$")
    private String studentId;

    @Column(length = 255, nullable = true, unique = true)
    private String githubUsername;

    @OneToMany(mappedBy = "student")
    private List<GroupMembership> memberships;

    @OneToMany(mappedBy = "invitee")
    private List<GroupInvitation> invitations;
}
