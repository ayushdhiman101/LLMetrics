package com.llmgateway.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("prompts")
public record Prompt(
        @Id UUID id,
        @Column("tenant_id") UUID tenantId,
        String name,
        @Column("created_at") LocalDateTime createdAt
) {}
