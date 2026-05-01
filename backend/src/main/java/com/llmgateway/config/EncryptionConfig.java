package com.llmgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

@Configuration
public class EncryptionConfig {

    @Value("${encryption.key}")
    private String hexKey;

    @Bean
    public SecretKey aesSecretKey() {
        byte[] keyBytes = HexFormat.of().parseHex(hexKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "encryption.key must be exactly 32 bytes (64 hex chars); got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
