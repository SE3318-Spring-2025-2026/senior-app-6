package com.senior.spm.service;

import org.springframework.stereotype.Service;

@Service
public class TermConfigService {

    /**
     * Retrieves the identifier of the currently active academic term.
     * <p>
     * Used throughout the system to filter groups, schedule windows, and other term-specific resources.
     * Currently returns a placeholder value; will be extended to fetch from system_config table.
     *
     * @return String identifier of the active term (e.g., "2024-FALL", "2025-SPRING")
     *         Currently returns "PLACEHOLDER-TERM"
     * @throws RuntimeException if system configuration cannot be retrieved
     *
     * @see #getMaxTeamSize()
     */
    public String getActiveTermId() {
        // TODO: Implement by fetching from system_config table
        // For now, return a placeholder
        return "PLACEHOLDER-TERM";
    }

    /**
     * Retrieves the maximum number of students allowed per project group.
     * <p>
     * Used for validation during:
     * <ul>
     *   <li>Group creation (student-initiated)</li>
     *   <li>Coordinator force-add operations (checks: currentMembers + pendingInvitations >= maxTeamSize)</li>
     * </ul>
     * <p>
     * The calculation includes both current members and pending group invitations to prevent
     * over-commitment of group resources.
     *
     * @return maximum number of members (including pending invitations) allowed per group
     *         Default value: 5 members per group
     *
     * @see com.senior.spm.service.GroupService#coordinatorAddStudent(java.util.UUID, String)
     */
    public int getMaxTeamSize() {
        // Default: 5 members per group
        // TODO: Implement by fetching from system_config table if configurable
        return 5;
    }
}
