package com.llmgateway.provider.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAIChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        @JsonProperty("stream_options") StreamOptions streamOptions
) {
    public record Message(String role, String content) {}

    public record StreamOptions(
            @JsonProperty("include_usage") boolean includeUsage
    ) {}
}
