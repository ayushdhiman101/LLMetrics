package com.llmgateway.dto.prompt;

import com.llmgateway.domain.PromptVersion;

import java.time.LocalDateTime;
import java.util.UUID;

public record PromptVersionResponse(
        UUID id,
        UUID promptId,
        String version,
        String template,
        String description,
        String changelog,
        boolean isActive,
        LocalDateTime createdAt
) {
    public static PromptVersionResponse from(PromptVersion v) {
        return new PromptVersionResponse(
                v.id(),
                v.promptId(),
                v.version(),
                v.template(),
                v.description(),
                v.changelog(),
                Boolean.TRUE.equals(v.isActive()),
                v.createdAt()
        );
    }
}
