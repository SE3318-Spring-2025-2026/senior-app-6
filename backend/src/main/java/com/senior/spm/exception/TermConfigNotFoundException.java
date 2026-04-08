package com.senior.spm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class TermConfigNotFoundException extends RuntimeException {
    public TermConfigNotFoundException(String message) {
        super(message);
    }
}
