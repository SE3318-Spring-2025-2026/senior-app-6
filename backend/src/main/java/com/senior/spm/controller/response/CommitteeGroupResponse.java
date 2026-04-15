package com.senior.spm.controller.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeGroupResponse {

    private UUID id;
    private UUID groupId;
    private String groupName;
    private String status;
}