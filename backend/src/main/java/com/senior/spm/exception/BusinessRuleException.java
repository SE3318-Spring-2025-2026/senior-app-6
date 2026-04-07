package com.senior.spm.exception;

/**
 * Exception thrown when a business rule validation fails.
 * Covers: student already in group, max team size exceeded, group already disbanded, etc.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
