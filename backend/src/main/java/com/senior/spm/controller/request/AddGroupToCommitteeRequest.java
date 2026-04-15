package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddGroupToCommitteeRequest {

    @NotNull(message = "Group ID is required")
    private java.util.UUID groupId;
}