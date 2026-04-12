package com.senior.spm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.DuplicateInvitationException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.InvitationNotFoundException;
import com.senior.spm.exception.InvitationNotPendingException;
import com.senior.spm.service.InvitationService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class InvitationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;


    @MockBean
    private InvitationService invitationService;

    @Test
    @DisplayName("POST /invitations -> Throwing DuplicateInvitationException should return 409 Conflict")
    void sendInvitation_duplicate_returns409() throws Exception {
        when(invitationService.sendInvitation(any(), any(), anyString()))
            .thenThrow(new DuplicateInvitationException("Already invited"));

        mockMvc.perform(post("/api/groups/{groupId}/invitations", UUID.randomUUID())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStudentId\":\"23070000001\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /respond -> Throwing InvitationNotFoundException should return 404 Not Found")
    void respondToInvitation_notFound_returns404() throws Exception {
        when(invitationService.respondToInvitation(any(), any(), anyBoolean()))
            .thenThrow(new InvitationNotFoundException("Not found"));

        mockMvc.perform(patch("/api/invitations/{id}/respond", UUID.randomUUID())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /respond -> Throwing InvitationNotPendingException should return 400 Bad Request")
    void respondToInvitation_notPending_returns400() throws Exception {
        when(invitationService.respondToInvitation(any(), any(), anyBoolean()))
            .thenThrow(new InvitationNotPendingException("Not pending"));

        mockMvc.perform(patch("/api/invitations/{id}/respond", UUID.randomUUID())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /invitations/{id} -> Throwing ForbiddenException should return 403 Forbidden")
    void cancelInvitation_forbidden_returns403() throws Exception {
        when(invitationService.cancelInvitation(any(), any()))
            .thenThrow(new ForbiddenException("Not leader"));

        mockMvc.perform(delete("/api/invitations/{id}", UUID.randomUUID())
                .with(authentication(studentAuth())))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /invitations -> Throwing BusinessRuleException should return 400 Bad Request")
    void sendInvitation_businessRule_returns400() throws Exception {
        when(invitationService.sendInvitation(any(), any(), anyString()))
            .thenThrow(new BusinessRuleException("Capacity reached"));

        mockMvc.perform(post("/api/groups/{groupId}/invitations", UUID.randomUUID())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStudentId\":\"23070000001\"}"))
            .andExpect(status().isBadRequest());
    }

    private Authentication studentAuth() {
        return new UsernamePasswordAuthenticationToken(
            UUID.randomUUID().toString(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }
}
