package com.llmgateway.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("users")
public record User(
        @Id UUID id,
        @Column("tenant_id") UUID tenantId,
        String email,
        @Column("password_hash") String passwordHash,
        @Column("created_at") LocalDateTime createdAt
) {}
