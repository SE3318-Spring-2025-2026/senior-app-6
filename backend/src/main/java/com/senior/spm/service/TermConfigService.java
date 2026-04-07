package com.senior.spm.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class TermConfigService {

    /**
     * Get active term ID for the current semester
     * This should be fetched from system_config table or similar
     * 
     * @return UUID of the active term
     */
    public UUID getActiveTermId() {
        // TODO: Implement by fetching from system_config table
        // For now, return a placeholder
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
