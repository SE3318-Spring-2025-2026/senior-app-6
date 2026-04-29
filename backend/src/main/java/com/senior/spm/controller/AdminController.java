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
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.AiValidationService;
import com.senior.spm.service.EncryptionService;
import com.senior.spm.service.StaffUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String LLM_KEY_CONFIG = "llm_api_key";

    private final StaffUserService staffUserService;
    private final SystemConfigRepository systemConfigRepository;
    private final EncryptionService encryptionService;
    private final AiValidationService aiValidationService;

    public AdminController(StaffUserService staffUserService,
                           SystemConfigRepository systemConfigRepository,
                           EncryptionService encryptionService,
                           AiValidationService aiValidationService) {
        this.staffUserService = staffUserService;
        this.systemConfigRepository = systemConfigRepository;
        this.encryptionService = encryptionService;
        this.aiValidationService = aiValidationService;
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
        return systemConfigRepository.findById(LLM_KEY_CONFIG)
                .map(config -> {
                    String plain = encryptionService.decrypt(config.getConfigValue());
                    return ResponseEntity.ok(new LlmConfigResponse(true, maskKey(plain)));
                })
                .orElseGet(() -> ResponseEntity.ok(new LlmConfigResponse(false, null)));
    }

    @PutMapping("/llm-config")
    public ResponseEntity<Map<String, String>> updateLlmConfig(@Valid @RequestBody LlmConfigRequest request) {
        String encrypted = encryptionService.encrypt(request.getApiKey());

        SystemConfig config = systemConfigRepository.findById(LLM_KEY_CONFIG)
                .orElseGet(SystemConfig::new);
        config.setConfigKey(LLM_KEY_CONFIG);
        config.setConfigValue(encrypted);
        systemConfigRepository.save(config);

        aiValidationService.invalidateKeyCache();

        return ResponseEntity.ok(Map.of("message", "LLM API key updated successfully"));
    }

    private static String maskKey(String key) {
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
