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
}
