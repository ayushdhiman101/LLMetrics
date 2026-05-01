package com.llmgateway.provider;

import java.util.Map;
import java.util.UUID;

public record ResolvedPromptRequest(
        UUID tenantId,
        String promptName,
        String version,
        String resolvedContent,
        String model,
        String strategy,
        Map<String, String> providerKeys
) {
    public ResolvedPromptRequest withModel(String newModel) {
        return new ResolvedPromptRequest(
                tenantId, promptName, version, resolvedContent, newModel, strategy, providerKeys);
    }
}
