package com.senior.spm.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class InvitationResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesWithoutNullFields() throws Exception {
        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(UUID.randomUUID());
        response.setStatus("DECLINED");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"invitationId\"");
        assertThat(json).contains("\"status\":\"DECLINED\"");
        assertThat(json).doesNotContain("groupName");
        assertThat(json).doesNotContain("teamLeaderStudentId");
        assertThat(json).doesNotContain("targetStudentId");
    }
}
