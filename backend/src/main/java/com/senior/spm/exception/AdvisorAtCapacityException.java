package com.senior.spm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AdvisorAtCapacityException extends RuntimeException {
    public AdvisorAtCapacityException(String message) {
        super(message);
    }
}
