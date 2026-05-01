package com.llmgateway.dto.prompt;

public record CreateVersionRequest(
        String version,
        String template,
        String description,
        String changelog,
        Boolean isActive
) {}
