package com.senior.spm.exception;

/**
 * Exception thrown when a requested student cannot be found.
 * Thrown during coordinator add/remove operations when student ID doesn't exist.
 */
public class StudentNotFoundException extends NotFoundException {
    public StudentNotFoundException(String message) {
        super(message);
    }
}
