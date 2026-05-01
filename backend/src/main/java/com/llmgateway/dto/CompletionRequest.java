package com.llmgateway.dto;

import java.util.List;
import java.util.Map;

public record CompletionRequest(
        String promptName,
        String version,
        Map<String, String> variables,
        String message,
        List<String> models,
        String strategy
) {}
