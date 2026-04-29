package com.senior.spm.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.LlmConfigRequest;
import com.senior.spm.controller.request.RegisterProfessorRequest;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.LlmConfigResponse;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.service.LlmConfigService;
import com.senior.spm.service.StaffUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StaffUserService staffUserService;
    private final LlmConfigService llmConfigService;

    public AdminController(StaffUserService staffUserService,
                           LlmConfigService llmConfigService) {
        this.staffUserService = staffUserService;
        this.llmConfigService = llmConfigService;
    }

    @PostMapping("/register-professor")
    public ResponseEntity<?> registerProfessor(@Valid @RequestBody RegisterProfessorRequest request) {
        var mail = request.getMail();

        try {
            var token = staffUserService.registerProfessor(mail);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("resetToken", token));
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorMessage("Professor with same mail already exists"));
        }
    }

    @GetMapping("/llm-config")
    public ResponseEntity<LlmConfigResponse> getLlmConfig() {
        return ResponseEntity.ok(llmConfigService.getLlmConfig());
    }

    @PutMapping("/llm-config")
    public ResponseEntity<Map<String, String>> updateLlmConfig(@Valid @RequestBody LlmConfigRequest request) {
        llmConfigService.updateLlmKey(request.getApiKey());
        return ResponseEntity.ok(Map.of("message", "LLM API key updated successfully"));
    }
}
