package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.senior.spm.exception.EncryptionException;

/**
 * Unit tests for EncryptionService (AES-256-GCM).
 * Issue: #49 — Tool Binding & Encryption Orchestration.
 */
class EncryptionServiceTest {

    private EncryptionService service;

    // 32 zero-bytes encoded as Base64 — valid AES-256 key for unit tests only
    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @BeforeEach
    void setUp() {
        service = new EncryptionService();
        ReflectionTestUtils.setField(service, "encodedKey", VALID_KEY);
        service.init();
    }

    // ── init() key validation ──────────────────────────────────────────────

    @Test
    void init_validKey_doesNotThrow() {
        EncryptionService fresh = new EncryptionService();
        ReflectionTestUtils.setField(fresh, "encodedKey", VALID_KEY);
        fresh.init(); // must not throw
    }

    @Test
    void init_keyTooShort_throwsIllegalStateException() {
        EncryptionService fresh = new EncryptionService();
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // 16 bytes — AES-128, not 256
        ReflectionTestUtils.setField(fresh, "encodedKey", shortKey);

        assertThatThrownBy(fresh::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void init_keyTooLong_throwsIllegalStateException() {
        EncryptionService fresh = new EncryptionService();
        String longKey = Base64.getEncoder().encodeToString(new byte[64]);
        ReflectionTestUtils.setField(fresh, "encodedKey", longKey);

        assertThatThrownBy(fresh::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    // ── encrypt() ─────────────────────────────────────────────────────────

    @Test
    void encrypt_outputIsNotPlaintext() {
        assertThat(service.encrypt("my-secret-pat")).isNotEqualTo("my-secret-pat");
    }

    @Test
    void encrypt_outputIsBase64UrlEncoded() {
        String result = service.encrypt("token");
        // Base64URL must not contain standard Base64 chars + or /
        assertThat(result).doesNotContain("+", "/");
        // Must decode without exception
        assertThat(Base64.getUrlDecoder().decode(result)).isNotEmpty();
    }

    @Test
    void encrypt_twoCallsProduceDifferentCiphertexts() {
        // Fresh 12-byte random IV per call — same plaintext must produce different ciphertexts
        String first = service.encrypt("same-plaintext");
        String second = service.encrypt("same-plaintext");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void encrypt_outputContainsIvPlusCiphertextPlusTag() {
        // Minimum encoded length: 12 bytes IV + 1 byte plaintext + 16 bytes auth tag = 29 bytes
        // Base64Url of 29 bytes = ceil(29 * 4/3) = 39 chars
        String result = service.encrypt("x");
        byte[] raw = Base64.getUrlDecoder().decode(result);
        assertThat(raw.length).isGreaterThanOrEqualTo(12 + 1 + 16);
    }

    // ── decrypt() ─────────────────────────────────────────────────────────

    @Test
    void roundtrip_githubPat_decryptMatchesOriginal() {
        String original = "ghp_test_github_pat_1234567890abcdef";
        assertThat(service.decrypt(service.encrypt(original))).isEqualTo(original);
    }

    @Test
    void roundtrip_jiraApiToken_decryptMatchesOriginal() {
        String original = "ATATT3xFfGF0test_jira_token_abcdefghijklmnop";
        assertThat(service.decrypt(service.encrypt(original))).isEqualTo(original);
    }

    @Test
    void roundtrip_longToken_decryptMatchesOriginal() {
        // Real API tokens can exceed 200 characters
        String original = "a".repeat(300);
        assertThat(service.decrypt(service.encrypt(original))).isEqualTo(original);
    }

    @Test
    void roundtrip_specialCharacters_decryptMatchesOriginal() {
        // Tokens may contain URL-unsafe chars — must survive Base64Url encoding/decoding
        String original = "tok=abc/def+ghi==?&";
        assertThat(service.decrypt(service.encrypt(original))).isEqualTo(original);
    }

    @Test
    void decrypt_tamperedCiphertext_throwsEncryptionException() {
        String encrypted = service.encrypt("secret-token");
        byte[] raw = Base64.getUrlDecoder().decode(encrypted);

        // Flip the last byte — sits inside the 16-byte GCM auth tag, guarantees tag mismatch
        raw[raw.length - 1] ^= 0xFF;
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("tampered");
    }

    @Test
    void decrypt_truncatedCiphertext_throwsEncryptionException() {
        String encrypted = service.encrypt("secret");
        // Strip last 8 bytes — corrupts auth tag
        byte[] raw = Base64.getUrlDecoder().decode(encrypted);
        byte[] truncated = java.util.Arrays.copyOf(raw, raw.length - 8);
        String bad = Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);

        assertThatThrownBy(() -> service.decrypt(bad))
                .isInstanceOf(EncryptionException.class);
    }
}
