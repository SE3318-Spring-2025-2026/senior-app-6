package com.senior.spm.controller.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class AddGroupsToCommitteeRequest {

    @NotEmpty
    private List<UUID> groupIds;
}
