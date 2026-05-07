package com.senior.spm.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.SystemConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @Transactional
    public void updateConfig(String activeTermId, Integer maxTeamSize) {
        if (activeTermId != null) {
            upsert("active_term_id", activeTermId);
        }
        if (maxTeamSize != null) {
            upsert("max_team_size", String.valueOf(maxTeamSize));
        }
    }

    private void upsert(String key, String value) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigKey(key);
                    return c;
                });
        config.setConfigValue(value);
        systemConfigRepository.save(config);
    }
}
