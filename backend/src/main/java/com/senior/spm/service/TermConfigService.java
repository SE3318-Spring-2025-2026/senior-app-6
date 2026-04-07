package com.senior.spm.service;

import org.springframework.stereotype.Service;

@Service
public class TermConfigService {

    /**
     * Get active term ID for the current semester
     * This should be fetched from system_config table or similar
     * Returns term as String (e.g., "2024-FALL", "2025-SPRING")
     * 
     * @return String identifier of the active term
     */
    public String getActiveTermId() {
        // TODO: Implement by fetching from system_config table
        // For now, return a placeholder
        return "PLACEHOLDER-TERM";
    }

    /**
     * Get maximum team size for group formation
     * Used for validation during group creation and coordinator force-add
     * 
     * @return maximum number of members allowed per group
     */
    public int getMaxTeamSize() {
        // Default: 5 members per group
        // TODO: Implement by fetching from system_config table if configurable
        return 5;
    }
}
