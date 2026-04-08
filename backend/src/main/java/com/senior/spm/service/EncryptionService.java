package com.senior.spm.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.senior.spm.exception.EncryptionException;

import jakarta.annotation.PostConstruct;

/**
 * AES-256-GCM symmetric encryption service for sensitive tokens stored at rest
 * (GitHub PATs, JIRA API tokens).
 *
 * <p>Output format: {@code Base64Url( IV[12] || ciphertext || authTag[16] )}
 * where IV is randomly generated per call and the 128-bit GCM auth tag is
 * appended to the ciphertext by the JCE provider.
 *
 * <p>The encryption key is read from {@code encryption.key} in
 * {@code application.properties} as a Base64-encoded value. It MUST decode to
 * exactly 32 bytes (256 bits). The key is validated once at startup via
 * {@link #init()} and an {@link IllegalStateException} is thrown if the length
 * is wrong — this prevents the application from starting with a bad key.
 *
 * <p>Acceptance criteria from Issue #49:
 * <ul>
 *   <li>Encryption requires a 32-byte key — validated at startup.</li>
 *   <li>Throws unchecked {@link EncryptionException} on cryptographic failure
 *       or tampering (AEADBadTagException on decrypt).</li>
 *   <li>Tokens are never saved as plaintext — callers store only the
 *       Base64Url-encoded ciphertext returned by {@link #encrypt(String)}.</li>
 * </ul>
 *
 * <p>Referenced by: DFD 2.4 (bind JIRA), DFD 2.5 (bind GitHub).
 * Issue: #49 — [Backend] Tool Binding &amp; Encryption Orchestration.
 */
@Service
public class EncryptionService {

    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;     // 96-bit nonce — recommended for GCM
    private static final int GCM_TAG_BITS = 128;       // 128-bit authentication tag
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    @Value("${encryption.key}")
    private String encodedKey;

    private SecretKey secretKey;

    /**
     * Validates and loads the AES key from application properties.
     * Called automatically by Spring after dependency injection.
     *
     * @throws IllegalStateException if the decoded key is not exactly 32 bytes
     */
    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                "encryption.key must decode to exactly 32 bytes (256 bits); got " + keyBytes.length + " bytes"
            );
        }
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts {@code plaintext} using AES-256-GCM with a freshly generated IV.
     *
     * <p>A new 12-byte IV is generated via {@link SecureRandom} for every call,
     * ensuring that encrypting the same plaintext twice produces different
     * ciphertexts (semantic security).
     *
     * @param plaintext the sensitive string to encrypt (e.g., a GitHub PAT)
     * @return Base64Url-encoded string in the format {@code IV || ciphertext || authTag}
     * @throws EncryptionException if the JCE provider reports a cryptographic error
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            // Java's GCM implementation appends the auth tag to the ciphertext output
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Concatenate: IV (12 bytes) || ciphertext+authTag
            byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);

        } catch (GeneralSecurityException ex) {
            throw new EncryptionException("Encryption failed", ex);
        }
    }

    /**
     * Decrypts a ciphertext produced by {@link #encrypt(String)}.
     *
     * <p>The GCM authentication tag is verified automatically by the JCE provider
     * before any plaintext is returned. If the ciphertext was tampered with, an
     * {@link AEADBadTagException} is thrown internally and wrapped in an
     * {@link EncryptionException}.
     *
     * @param encoded Base64Url-encoded ciphertext as returned by {@link #encrypt(String)}
     * @return the original plaintext string
     * @throws EncryptionException if the ciphertext was tampered with or the
     *                             JCE provider reports a cryptographic error
     */
    public String decrypt(String encoded) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(encoded);

            byte[] iv = Arrays.copyOfRange(raw, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(raw, IV_LENGTH_BYTES, raw.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (AEADBadTagException ex) {
            // Auth tag mismatch — the ciphertext was tampered with
            throw new EncryptionException("Decryption failed: ciphertext has been tampered with", ex);
        } catch (GeneralSecurityException ex) {
            throw new EncryptionException("Decryption failed", ex);
        }
    }
}
