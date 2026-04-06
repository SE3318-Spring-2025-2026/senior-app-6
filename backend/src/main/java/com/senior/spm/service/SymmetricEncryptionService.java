package com.senior.spm.service;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SymmetricEncryptionService {

    @Value("${encryption.secret}")
    private String SECRET;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int AES_KEY_BIT = 256;

    public String encrypt(String plainText) {
        try {
            var iv = generateIv();
            var key = decodeKey(SECRET);

            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            var cipherText = cipher.doFinal(plainText.getBytes());

            var cipherTextWithIv = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();

            return Base64.getEncoder().encodeToString(cipherTextWithIv);
        } catch (InvalidAlgorithmParameterException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | BadPaddingException
                | IllegalBlockSizeException
                | NoSuchPaddingException e) {
            throw new RuntimeException("Error occurred during encryption", e);
        }
    }

    public String decrypt(String cipherTextWithIvBase64) {
        try {
            var decode = Base64.getDecoder().decode(cipherTextWithIvBase64);

            var byteBuffer = ByteBuffer.wrap(decode);
            var iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);
            var cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            var key = decodeKey(SECRET);

            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            return new String(cipher.doFinal(cipherText));
        } catch (InvalidAlgorithmParameterException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | BadPaddingException
                | IllegalBlockSizeException
                | NoSuchPaddingException e) {
            throw new RuntimeException("Error occurred during decryption", e);
        }
    }

    public String generateKey() throws Exception {
        var keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_BIT);
        return Base64.getEncoder().encodeToString(keyGenerator.generateKey().getEncoded());
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH_BYTE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private SecretKey decodeKey(String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }
}
