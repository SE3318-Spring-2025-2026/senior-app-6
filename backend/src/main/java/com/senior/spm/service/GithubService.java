package com.senior.spm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class GithubService {

    private final RestClient authClient;
    private final RestClient apiClient;

    public GithubService(RestClient.Builder restClientBuilder) {
        authClient = restClientBuilder.clone()
                .baseUrl("https://github.com")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        apiClient = restClientBuilder.clone()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "SPMApp")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2026-03-10")
                .build();
    }

    @Value("${GITHUB_CLIENT_ID}")
    private String GITHUB_CLIENT_ID;

    @Value("${GITHUB_CLIENT_SECRET}")
    private String GITHUB_CLIENT_SECRET;

    private record GithubTokenRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("client_secret") String clientSecret,
            @JsonProperty("code") String code) {

    }

    public record GithubTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("scope") String scope) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubUserResponse(
            @JsonProperty("login") String login,
            @JsonProperty("id") Long id,
            @JsonProperty("avatar_url") String avatarUrl) {

    }

    public GithubTokenResponse exchangeCodeForAccessToken(String code) {
        var tokenRequest = new GithubTokenRequest(
                GITHUB_CLIENT_ID,
                GITHUB_CLIENT_SECRET,
                code);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", tokenRequest.clientId());
        formData.add("client_secret", tokenRequest.clientSecret());
        formData.add("code", tokenRequest.code());
        formData.add("redirect_uri", "http://localhost:3000/auth/github-callback");

        return authClient.post()
                .uri("/login/oauth/access_token")
                .body(formData)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve()
                .body(GithubTokenResponse.class);
    }

    public GithubUserResponse getGithubUser(String accessToken) {
        return apiClient.get()
                .uri("/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GithubUserResponse.class);
    }
}
