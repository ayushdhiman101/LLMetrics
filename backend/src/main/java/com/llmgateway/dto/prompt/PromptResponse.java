package com.llmgateway.dto.prompt;

import com.llmgateway.domain.Prompt;

import java.time.LocalDateTime;
import java.util.UUID;

public record PromptResponse(
        UUID id,
        UUID tenantId,
        String name,
        LocalDateTime createdAt
) {
    public static PromptResponse from(Prompt p) {
        return new PromptResponse(p.id(), p.tenantId(), p.name(), p.createdAt());
    }
}
