package com.llmgateway.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("usage_events")
public record UsageEvent(
        @Id UUID id,
        @Column("tenant_id") UUID tenantId,
        @Column("prompt_id") UUID promptId,
        @Column("prompt_version") String promptVersion,
        String provider,
        String model,
        @Column("input_tokens") Integer inputTokens,
        @Column("output_tokens") Integer outputTokens,
        @Column("cost_usd") BigDecimal costUsd,
        @Column("latency_ms") Integer latencyMs,
        @Column("created_at") LocalDateTime createdAt
) {}
