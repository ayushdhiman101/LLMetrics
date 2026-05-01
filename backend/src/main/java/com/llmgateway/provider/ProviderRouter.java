package com.llmgateway.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderRouter {

    private static final String DEFAULT_PROVIDER = "gemini";

    private final Map<String, ProviderAdapter> byName;

    public ProviderRouter(List<ProviderAdapter> adapters) {
        this.byName = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(ProviderAdapter::providerName, Function.identity()));
    }

    public ProviderAdapter route(String model) {
        String providerName = providerFor(model);
        ProviderAdapter adapter = byName.get(providerName);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for provider: " + providerName);
        }
        return adapter;
    }

    private static String providerFor(String model) {
        if (model == null || model.isBlank()) return DEFAULT_PROVIDER;
        if (model.startsWith("gpt-")) return "openai";
        if (model.startsWith("gemini-")) return "gemini";
        if (model.startsWith("claude-")) return "anthropic";
        throw new IllegalArgumentException("Unknown model family: " + model);
    }
}
