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

    /**
     * Retrieves the active term ID for the current semester from the system configuration.
     *
     * @return The active term ID as a String (e.g., "2026-SPRING").
     * @throws TermConfigNotFoundException if the 'active_term_id' key is missing from the database.
     */
    public String getActiveTermId() {
        SystemConfig config = systemConfigRepository.findByConfigKey("active_term_id")
                .orElseThrow(() -> new TermConfigNotFoundException("active_term_id not found in system_config"));

        String value = config.getConfigValue();
        if (value == null || value.isBlank()) {
            throw new TermConfigNotFoundException("active_term_id contains a blank value");
        }

        return value;
    }

    /**
     * Retrieves the maximum allowed team size from the system configuration.
     * Validates that the stored string value is a proper integer.
     *
     * @return The maximum team size as an Integer.
     * @throws TermConfigNotFoundException if the key is missing OR if the value is not a valid number.
     */
    public Integer getMaxTeamSize() {
        SystemConfig config = systemConfigRepository.findByConfigKey("max_team_size")
                .orElseThrow(() -> new TermConfigNotFoundException("max_team_size not found in system_config"));

        String value = config.getConfigValue();
        if (value == null || value.isBlank()) {
            throw new TermConfigNotFoundException("max_team_size config value is not a valid integer: " + value);
        }

        try {
            int maxTeamSize = Integer.parseInt(value);
            if (maxTeamSize <= 0) {
                throw new TermConfigNotFoundException("max_team_size must be a positive integer: " + value);
            }

            return maxTeamSize;
        } catch (NumberFormatException e) {
            throw new TermConfigNotFoundException("max_team_size config value is not a valid integer: " + value);
        }
    }
}
