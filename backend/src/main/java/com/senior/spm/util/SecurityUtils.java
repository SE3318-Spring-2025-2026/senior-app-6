package com.senior.spm.util;

import java.util.UUID;

import org.springframework.security.core.Authentication;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID extractPrincipalUUID(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
