package com.llmgateway.provider;

import reactor.core.publisher.Flux;

public interface ProviderAdapter {
    Flux<ProviderEvent> stream(ResolvedPromptRequest request);
    String providerName();
}
