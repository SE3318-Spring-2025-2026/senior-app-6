package com.senior.spm.service;

import com.senior.spm.controller.response.LlmConfigResponse;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.exception.EncryptionException;
import com.senior.spm.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {

    public static final String LLM_KEY_CONFIG = "llm_api_key";

    private final SystemConfigRepository systemConfigRepository;
    private final EncryptionService encryptionService;
    private final AiValidationService aiValidationService;

    public LlmConfigResponse getLlmConfig() {
        return systemConfigRepository.findById(LLM_KEY_CONFIG)
                .map(config -> {
                    try {
                        String plain = encryptionService.decrypt(config.getConfigValue());
                        return new LlmConfigResponse(true, maskKey(plain));
                    } catch (EncryptionException ex) {
                        log.warn("Failed to decrypt LLM API key — stored ciphertext may be corrupt: {}",
                                ex.getMessage());
                        return new LlmConfigResponse(true, "****");
                    }
                })
                .orElseGet(() -> new LlmConfigResponse(false, null));
    }

    public void updateLlmKey(String apiKey) {
        String encrypted = encryptionService.encrypt(apiKey);

        SystemConfig config = systemConfigRepository.findById(LLM_KEY_CONFIG)
                .orElseGet(SystemConfig::new);
        config.setConfigKey(LLM_KEY_CONFIG);
        config.setConfigValue(encrypted);
        systemConfigRepository.save(config);

        aiValidationService.invalidateKeyCache();
    }

    static String maskKey(String key) {
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
