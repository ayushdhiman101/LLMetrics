package com.llmgateway.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("tenants")
public record Tenant(
        @Id UUID id,
        String name,
        @Column("created_at") LocalDateTime createdAt
) {}
