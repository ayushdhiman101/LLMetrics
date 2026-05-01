package com.llmgateway.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnthropicMessageRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") int maxTokens,
        boolean stream
) {
    public record Message(String role, String content) {}
}
