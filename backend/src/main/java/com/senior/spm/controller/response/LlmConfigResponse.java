package com.senior.spm.controller.response;

import org.springframework.lang.Nullable;

public record LlmConfigResponse(boolean configured, @Nullable String maskedKey) {}
