package com.senior.spm.controller.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignCommitteeGroupsRequest {

    @NotEmpty(message = "Group ids list cannot be empty")
    private List<UUID> groupIds;
}