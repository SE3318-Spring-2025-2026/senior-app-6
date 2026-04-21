package com.senior.spm.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_group")
@Getter
@Setter
@NoArgsConstructor
public class ProjectGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupStatus status;

    @Column(nullable = false)
    private String termId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 500, nullable = true)
    private String jiraSpaceUrl;

    @Column(length = 254, nullable = true)
    private String jiraEmail;

    @Column(length = 100, nullable = true)
    private String jiraProjectKey;

    @Column(length = 1024, nullable = true)
    private String encryptedJiraToken;

    @Column(length = 100, nullable = true)
    private String githubOrgName;

    @Column(length = 1024, nullable = true)
    private String encryptedGithubPat;

    @ManyToOne
    @JoinColumn(name = "advisor_id", nullable = true, foreignKey = @ForeignKey(name = "fk_project_group_advisor"))
    private StaffUser advisor;

    @OneToMany(mappedBy = "group")
    private List<GroupMembership> members;

    @OneToMany(mappedBy = "group")
    private List<GroupInvitation> invitations;

    @Version
    @Column(nullable = false)
    private Long version;

    public enum GroupStatus {
        FORMING,
        TOOLS_PENDING,
        TOOLS_BOUND,
        ADVISOR_ASSIGNED,
        DISBANDED;

        public boolean locksRoster() {
            return switch (this) {
                case TOOLS_BOUND, ADVISOR_ASSIGNED -> true;
                case FORMING, TOOLS_PENDING, DISBANDED -> false;
            };
        }
    }
}
