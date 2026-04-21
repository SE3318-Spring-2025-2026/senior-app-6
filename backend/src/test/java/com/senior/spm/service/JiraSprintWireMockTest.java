package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.service.dto.JiraIssueDto;

class JiraSprintWireMockTest {

    private static WireMockServer wireMockServer;
    private JiraSprintService jiraSprintService;

    private final EncryptionService fakeEncryptionService = new EncryptionService() {
        @Override
        public String decrypt(String cipherText) {
            return "fake-jira-api-token";
        }
    };

    @BeforeAll
    static void startServer() {
        wireMockServer = new WireMockServer(0); // Random port
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        RestClient.Builder builder = RestClient.builder();
        jiraSprintService = new JiraSprintService(builder, fakeEncryptionService);
    }

    @Test
    void testFetchSprintStories_Success_ReturnsMappedIssues() {
        ProjectGroup group = new ProjectGroup();
        group.setJiraEmail("test@example.com");
        group.setJiraSpaceUrl(wireMockServer.baseUrl());
        group.setEncryptedJiraToken("some-encrypted-token");

        String jsonResponse = "{ \"maxResults\": 50, \"startAt\": 0, \"total\": 3, \"issues\": [" +
                "{ \"key\": \"SPM-42\", \"fields\": { \"assignee\": { \"emailAddress\": \"student@example.com\" }, \"customfield_10016\": 3, \"description\": \"Implemented group creation\" } }," +
                "{ \"key\": \"SPM-43\", \"fields\": { \"customfield_10016\": 5 } }," +
                "{ \"key\": \"SPM-44\", \"fields\": { \"customfield_10016\": 8, \"description\": {\"type\":\"doc\", \"version\":1} } }" +
                "] }";

        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/rest/agile/1.0/sprint/200/issue?startAt=0"))
                .withHeader("Authorization", WireMock.containing("Basic "))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        List<JiraIssueDto> result = jiraSprintService.fetchSprintStories(group, "200");

        assertThat(result).hasSize(3);

        JiraIssueDto issue1 = result.get(0);
        assertThat(issue1.issueKey()).isEqualTo("SPM-42");
        assertThat(issue1.assigneeEmail()).isEqualTo("student@example.com");
        assertThat(issue1.storyPoints()).isEqualTo(3);
        assertThat(issue1.description()).isEqualTo("Implemented group creation");

        JiraIssueDto issue2 = result.get(1);
        assertThat(issue2.issueKey()).isEqualTo("SPM-43");
        assertThat(issue2.assigneeEmail()).isNull();
        assertThat(issue2.storyPoints()).isEqualTo(5);
        assertThat(issue2.description()).isNull();

        JiraIssueDto issue3 = result.get(2);
        assertThat(issue3.issueKey()).isEqualTo("SPM-44");
        assertThat(issue3.storyPoints()).isEqualTo(8);
        assertThat(issue3.description()).contains("\"type\":\"doc\"");
    }

    @Test
    void testFetchSprintStories_With401Unauthorized_ReturnsEmptyList_NoExceptions() {
        ProjectGroup group = new ProjectGroup();
        group.setJiraEmail("test@example.com");
        group.setJiraSpaceUrl(wireMockServer.baseUrl());
        group.setEncryptedJiraToken("some-encrypted-token");

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/rest/agile/1.0/sprint/201/issue"))
                .willReturn(WireMock.aResponse()
                        .withStatus(401)));

        assertThatCode(() -> {
            List<JiraIssueDto> result = jiraSprintService.fetchSprintStories(group, "201");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void testFetchSprintStories_With404NotFound_ReturnsEmptyList_NoExceptions() {
        ProjectGroup group = new ProjectGroup();
        group.setJiraEmail("test@example.com");
        group.setJiraSpaceUrl(wireMockServer.baseUrl());
        group.setEncryptedJiraToken("some-encrypted-token");

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/rest/agile/1.0/sprint/202/issue"))
                .willReturn(WireMock.aResponse()
                        .withStatus(404)));

        assertThatCode(() -> {
            List<JiraIssueDto> result = jiraSprintService.fetchSprintStories(group, "202");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }
}