package com.senior.spm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.exception.GitHubValidationException;
import com.senior.spm.exception.JiraValidationException;
import com.senior.spm.repository.ProjectGroupRepository;

@ExtendWith(MockitoExtension.class)
class ToolHealthServiceTest {

    @Mock
    private ProjectGroupRepository projectGroupRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private JiraValidationService jiraValidationService;
    @Mock
    private GitHubValidationService gitHubValidationService;

    @InjectMocks
    private ToolHealthService toolHealthService;

    @Test
    void checkAllToolHealth_updatesValidFlags() {
        ProjectGroup group1 = new ProjectGroup();
        group1.setGroupName("Group 1");
        group1.setEncryptedJiraToken("enc-jira");
        group1.setJiraSpaceUrl("url");
        group1.setJiraEmail("email");
        group1.setJiraProjectKey("key");

        ProjectGroup group2 = new ProjectGroup();
        group2.setGroupName("Group 2");
        group2.setEncryptedGithubPat("enc-github");
        group2.setGithubOrgName("org");
        group2.setGithubRepoName("repo");

        when(projectGroupRepository.findAll()).thenReturn(List.of(group1, group2));
        when(encryptionService.decrypt("enc-jira")).thenReturn("raw-jira");
        when(encryptionService.decrypt("enc-github")).thenReturn("raw-github");

        // group1 jira success, group2 github failure
        doThrow(new GitHubValidationException("Failed")).when(gitHubValidationService).validate(any(), any(), any());

        toolHealthService.checkAllToolHealth();

        verify(jiraValidationService).validate("url", "email", "key", "raw-jira");
        verify(gitHubValidationService).validate("org", "raw-github", "repo");
        
        verify(projectGroupRepository, times(2)).save(any());
        
        org.assertj.core.api.Assertions.assertThat(group1.getJiraTokenValid()).isTrue();
        org.assertj.core.api.Assertions.assertThat(group2.getGithubTokenValid()).isFalse();
    }

    @Test
    void checkAllToolHealth_handlesDecryptionFailure() {
        ProjectGroup group = new ProjectGroup();
        group.setEncryptedJiraToken("bad-enc");
        
        when(projectGroupRepository.findAll()).thenReturn(List.of(group));
        when(encryptionService.decrypt("bad-enc")).thenThrow(new RuntimeException("Decryption failed"));

        toolHealthService.checkAllToolHealth();

        org.assertj.core.api.Assertions.assertThat(group.getJiraTokenValid()).isFalse();
        verify(projectGroupRepository).save(group);
    }
}
