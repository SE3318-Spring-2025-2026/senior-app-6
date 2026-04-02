package com.senior.spm.controller.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GithubLoginResponse {

    private String token;
    private UserInfo userInfo;

    @Data
    @AllArgsConstructor
    public static class UserInfo {

        private UUID id;
        private String githubUsername;
        private String role;
    }
}
