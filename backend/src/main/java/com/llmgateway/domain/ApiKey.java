package com.llmgateway.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("api_keys")
public record ApiKey(
        @Id UUID id,
        @Column("tenant_id") UUID tenantId,
        @Column("key_hash") String keyHash,
        @Column("created_at") LocalDateTime createdAt
) {}
