package com.senior.spm.controller.response;

import java.util.UUID;

import com.senior.spm.entity.StaffUser;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private UserInfo userInfo;

    @Data
    @AllArgsConstructor
    public static class UserInfo {

        private UUID id;
        private String mail;
        private StaffUser.Role role;
        private boolean isFirstLogin;
    }
}
