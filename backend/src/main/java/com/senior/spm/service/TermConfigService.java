package com.senior.spm.service;

import com.senior.spm.entity.SystemConfig;
import com.senior.spm.exception.TermConfigNotFoundException;
import com.senior.spm.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TermConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public String getActiveTermId() {
        SystemConfig config = systemConfigRepository.findByConfigKey("active_term_id")
                .orElseThrow(() -> new TermConfigNotFoundException("active_term_id not found in system_config"));
        return config.getConfigValue();
    }

    public Integer getMaxTeamSize() {
        SystemConfig config = systemConfigRepository.findByConfigKey("max_team_size")
                .orElseThrow(() -> new TermConfigNotFoundException("max_team_size not found in system_config"));
        return Integer.parseInt(config.getConfigValue());
    }
}