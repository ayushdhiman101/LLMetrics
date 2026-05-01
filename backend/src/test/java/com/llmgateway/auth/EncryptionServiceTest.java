package com.llmgateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private static final String HEX_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = HexFormat.of().parseHex(HEX_KEY);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        encryptionService = new EncryptionService(secretKey);
    }

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String plaintext = "sk-my-secret-api-key";
        String encrypted = encryptionService.encrypt(plaintext);
        assertThat(encryptionService.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_sameInput_producesDifferentCiphertexts() {
        // AES-GCM uses a random IV so two encryptions of the same value must differ
        String plaintext = "my-api-key";
        String c1 = encryptionService.encrypt(plaintext);
        String c2 = encryptionService.encrypt(plaintext);
        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    void encrypt_outputIsValidBase64() {
        String encrypted = encryptionService.encrypt("hello");
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        // IV (12 bytes) + ciphertext + GCM tag (16 bytes): at minimum 28 bytes
        assertThat(decoded.length).isGreaterThanOrEqualTo(28);
    }

    @Test
    void decrypt_corruptedCiphertext_throwsIllegalStateException() {
        String validEncrypted = encryptionService.encrypt("hello");
        byte[] bytes = Base64.getDecoder().decode(validEncrypted);
        // flip a byte in the ciphertext region (after the 12-byte IV)
        bytes[15] ^= 0xFF;
        String corrupted = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> encryptionService.decrypt(corrupted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_invalidBase64_throwsIllegalStateException() {
        assertThatThrownBy(() -> encryptionService.decrypt("not valid base64!!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptDecrypt_longValue_roundTrips() {
        String longKey = "Bearer " + "a".repeat(512);
        assertThat(encryptionService.decrypt(encryptionService.encrypt(longKey))).isEqualTo(longKey);
    }

    @Test
    void encryptDecrypt_emptyString_roundTrips() {
        assertThat(encryptionService.decrypt(encryptionService.encrypt(""))).isEmpty();
    }
}
