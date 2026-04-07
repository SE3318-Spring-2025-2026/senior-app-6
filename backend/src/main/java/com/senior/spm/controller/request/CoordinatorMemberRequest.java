package com.senior.spm.controller.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoordinatorMemberRequest {
    private String studentId;
    private String action; // ADD or REMOVE
}
