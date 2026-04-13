package com.senior.spm;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.controller.response.GroupDetailResponse;
import com.senior.spm.controller.response.InvitationResponse;
import com.senior.spm.service.GroupService;
import com.senior.spm.service.GithubService;
import com.senior.spm.service.InvitationService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class InvitationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InvitationService invitationService;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private GithubService githubService;

    @Test
    void sendInvitation_returnsCreatedResponse() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();

        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitationId);
        response.setGroupId(groupId);
        response.setTargetStudentId("23070000002");
        response.setStatus("PENDING");
        response.setSentAt(LocalDateTime.now());

        when(invitationService.sendInvitation(eq(groupId), eq(requesterId), eq("23070000002"))).thenReturn(response);

        mockMvc.perform(post("/api/groups/{groupId}/invitations", groupId)
                .with(authentication(studentAuth(requesterId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetStudentId":"23070000002"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.invitationId").value(invitationId.toString()))
            .andExpect(jsonPath("$.groupId").value(groupId.toString()))
            .andExpect(jsonPath("$.targetStudentId").value("23070000002"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getGroupInvitations_returnsListResponse() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(UUID.randomUUID());
        response.setTargetStudentId("23070000002");
        response.setStatus("PENDING");
        response.setSentAt(LocalDateTime.now());

        when(invitationService.getGroupInvitations(groupId, requesterId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/groups/{groupId}/invitations", groupId)
                .with(authentication(studentAuth(requesterId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].targetStudentId").value("23070000002"))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getPendingInvitations_returnsInboxResponse() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitationId);
        response.setGroupId(groupId);
        response.setGroupName("Team Alpha");
        response.setStatus("PENDING");
        response.setTeamLeaderStudentId("23070000001");
        response.setSentAt(LocalDateTime.now());

        when(invitationService.getPendingInvitations(requesterId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/invitations/pending")
                .with(authentication(studentAuth(requesterId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].invitationId").value(invitationId.toString()))
            .andExpect(jsonPath("$[0].groupId").value(groupId.toString()))
            .andExpect(jsonPath("$[0].groupName").value("Team Alpha"))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].teamLeaderStudentId").value("23070000001"));
    }

    @Test
    void cancelInvitation_returnsStatusResponse() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();

        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitationId);
        response.setStatus("CANCELLED");

        when(invitationService.cancelInvitation(invitationId, requesterId)).thenReturn(response);

        mockMvc.perform(delete("/api/invitations/{invitationId}", invitationId)
                .with(authentication(studentAuth(requesterId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invitationId").value(invitationId.toString()))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void respondToInvitation_decline_returnsInvitationResponse() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();

        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitationId);
        response.setStatus("DECLINED");

        when(invitationService.respondToInvitation(invitationId, requesterId, false)).thenReturn(response);

        mockMvc.perform(patch("/api/invitations/{invitationId}/respond", invitationId)
                .with(authentication(studentAuth(requesterId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"accept":false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invitationId").value(invitationId.toString()))
            .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void respondToInvitation_accept_returnsGroupDetailResponse() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        GroupDetailResponse response = new GroupDetailResponse();
        response.setId(groupId);
        response.setGroupName("Team Alpha");
        response.setStatus("FORMING");

        when(invitationService.respondToInvitation(invitationId, requesterId, true)).thenReturn(response);

        mockMvc.perform(patch("/api/invitations/{invitationId}/respond", invitationId)
                .with(authentication(studentAuth(requesterId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AcceptRequest(true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(groupId.toString()))
            .andExpect(jsonPath("$.groupName").value("Team Alpha"))
            .andExpect(jsonPath("$.status").value("FORMING"));
    }

    private Authentication studentAuth(UUID studentId) {
        return new UsernamePasswordAuthenticationToken(
            studentId.toString(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    private record AcceptRequest(boolean accept) { }
}
