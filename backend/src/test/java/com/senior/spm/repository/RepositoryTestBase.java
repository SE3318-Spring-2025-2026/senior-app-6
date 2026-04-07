package com.senior.spm.repository;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;

/**
 * Shared factory methods for building test entities.
 * All data is in-memory (H2); each test rolls back after completion.
 */
abstract class RepositoryTestBase {

    @Autowired
    TestEntityManager em;

    protected Student makeStudent(String studentId) {
        Student s = new Student();
        s.setStudentId(studentId);
        return em.persistAndFlush(s);
    }

    protected StaffUser makeProfessor(String mail) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("hash");
        u.setRole(StaffUser.Role.Professor);
        return em.persistAndFlush(u);
    }

    protected ProjectGroup makeGroup(String name, String termId, ProjectGroup.GroupStatus status) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId(termId);
        g.setStatus(status);
        g.setCreatedAt(LocalDateTime.now());
        return em.persistAndFlush(g);
    }

    protected ProjectGroup makeGroupWithAdvisor(String name, String termId,
            ProjectGroup.GroupStatus status, StaffUser advisor) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId(termId);
        g.setStatus(status);
        g.setCreatedAt(LocalDateTime.now());
        g.setAdvisor(advisor);
        return em.persistAndFlush(g);
    }

    protected GroupMembership makeMembership(ProjectGroup group, Student student,
            GroupMembership.MemberRole role) {
        GroupMembership m = new GroupMembership();
        m.setGroup(group);
        m.setStudent(student);
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now());
        return em.persistAndFlush(m);
    }

    protected GroupInvitation makeInvitation(ProjectGroup group, Student invitee,
            GroupInvitation.InvitationStatus status) {
        GroupInvitation inv = new GroupInvitation();
        inv.setGroup(group);
        inv.setInvitee(invitee);
        inv.setStatus(status);
        inv.setSentAt(LocalDateTime.now());
        return em.persistAndFlush(inv);
    }

    protected AdvisorRequest makeAdvisorRequest(ProjectGroup group, StaffUser advisor,
            AdvisorRequest.RequestStatus status, LocalDateTime sentAt) {
        AdvisorRequest req = new AdvisorRequest();
        req.setGroup(group);
        req.setAdvisor(advisor);
        req.setStatus(status);
        req.setSentAt(sentAt);
        return em.persistAndFlush(req);
    }

    protected ScheduleWindow makeScheduleWindow(String termId, ScheduleWindow.WindowType type,
            LocalDateTime opens, LocalDateTime closes) {
        ScheduleWindow sw = new ScheduleWindow();
        sw.setTermId(termId);
        sw.setType(type);
        sw.setOpensAt(opens);
        sw.setClosesAt(closes);
        return em.persistAndFlush(sw);
    }

    /** Clears the 1st-level cache so JPQL bulk updates are visible on re-fetch. */
    protected void clearCache() {
        em.flush();
        em.getEntityManager().clear();
    }
}
