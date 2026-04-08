package com.senior.spm.exception;

/**
 * Unchecked exception thrown by EncryptionService on any cryptographic failure.
 *
 * Causes include:
 * - Key is not 32 bytes (detected at startup via @PostConstruct)
 * - Ciphertext has been tampered with (AEADBadTagException on decrypt)
 * - JVM is missing the required JCE provider (should never happen on Java 21)
 *
 * This exception is intentionally unchecked so callers are not forced to handle
 * cryptographic errors they cannot recover from programmatically.
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
