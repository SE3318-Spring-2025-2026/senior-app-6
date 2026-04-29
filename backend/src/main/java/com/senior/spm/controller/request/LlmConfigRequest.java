package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmConfigRequest {

    @NotBlank(message = "apiKey must not be blank")
    private String apiKey;
}
