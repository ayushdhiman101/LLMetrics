package com.llmgateway.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// One SSE data chunk. Anthropic uses several `type` values; we care about:
//   message_start         → message.usage.input_tokens
//   content_block_delta   → delta.text
//   message_delta         → usage.output_tokens (cumulative)
// Other types (ping, content_block_start/stop, message_stop) deserialize to mostly-null fields.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicStreamChunk(
        String type,
        Message message,
        Delta delta,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens
    ) {}
}
