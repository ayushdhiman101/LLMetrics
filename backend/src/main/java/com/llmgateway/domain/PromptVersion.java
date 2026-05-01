package com.llmgateway.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("prompt_versions")
public record PromptVersion(
        @Id UUID id,
        @Column("prompt_id") UUID promptId,
        String version,
        String template,
        String description,
        String changelog,
        @Column("is_active") Boolean isActive,
        @Column("created_at") LocalDateTime createdAt
) {}
