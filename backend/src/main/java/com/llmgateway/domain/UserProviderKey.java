package com.llmgateway.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("user_provider_keys")
public record UserProviderKey(
        @Id UUID id,
        @Column("user_id") UUID userId,
        String provider,
        @Column("encrypted_key") String encryptedKey,
        @Column("created_at") LocalDateTime createdAt,
        @Column("updated_at") LocalDateTime updatedAt
) {}
