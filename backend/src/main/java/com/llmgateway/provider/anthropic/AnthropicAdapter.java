package com.llmgateway.provider.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.provider.ProviderAdapter;
import com.llmgateway.provider.ProviderEvent;
import com.llmgateway.provider.ProviderUnavailableException;
import com.llmgateway.provider.ResolvedPromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AnthropicAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAdapter.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${providers.anthropic.default-model:claude-haiku-4-5}")
    private String defaultModel;

    @Value("${providers.anthropic.max-tokens:1024}")
    private int defaultMaxTokens;

    public AnthropicAdapter(
            @Qualifier("anthropicWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public Flux<ProviderEvent> stream(ResolvedPromptRequest request) {
        String model = request.model() != null ? request.model() : defaultModel;

        AnthropicMessageRequest body = new AnthropicMessageRequest(
                model,
                List.of(new AnthropicMessageRequest.Message("user", request.resolvedContent())),
                defaultMaxTokens,
                true
        );

        // Anthropic splits usage across two chunk types: input_tokens in message_start,
        // output_tokens in message_delta. Accumulate both, emit one combined Usage at the end.
        AtomicInteger inputTokens = new AtomicInteger(0);
        AtomicInteger outputTokens = new AtomicInteger(0);

        String apiKey = request.providerKeys() != null
                ? request.providerKeys().getOrDefault("anthropic", "") : "";

        Flux<ProviderEvent> tokens = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errBody -> {
                                    log.error("Anthropic {} error body: {}", response.statusCode(), errBody);
                                    if (errBody.contains("credit balance") || errBody.contains("insufficient_quota")) {
                                        return Mono.error(new ProviderUnavailableException("Anthropic: insufficient credits"));
                                    }
                                    return Mono.error(new RuntimeException("Anthropic " + response.statusCode() + ": " + errBody));
                                }))
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && line.startsWith("{"))
                .flatMapIterable(json -> parseChunk(json, inputTokens, outputTokens))
                .filter(Objects::nonNull)
                .onBackpressureBuffer(256)
                .timeout(Duration.ofSeconds(60));

        Flux<ProviderEvent> trailingUsage = Mono.fromSupplier(() -> {
            int in = inputTokens.get();
            int out = outputTokens.get();
            return (in > 0 || out > 0) ? new ProviderEvent.Usage(in, out) : null;
        }).filter(Objects::nonNull).cast(ProviderEvent.class).flux();

        return tokens.concatWith(trailingUsage);
    }

    private List<ProviderEvent> parseChunk(String json, AtomicInteger inputTokens, AtomicInteger outputTokens) {
        try {
            AnthropicStreamChunk chunk = objectMapper.readValue(json, AnthropicStreamChunk.class);
            if (chunk.type() == null) return List.of();
            switch (chunk.type()) {
                case "message_start" -> {
                    if (chunk.message() != null && chunk.message().usage() != null
                            && chunk.message().usage().inputTokens() != null) {
                        inputTokens.set(chunk.message().usage().inputTokens());
                    }
                }
                case "content_block_delta" -> {
                    if (chunk.delta() != null && chunk.delta().text() != null && !chunk.delta().text().isEmpty()) {
                        return List.of(new ProviderEvent.Token(chunk.delta().text()));
                    }
                }
                case "message_delta" -> {
                    if (chunk.usage() != null && chunk.usage().outputTokens() != null) {
                        outputTokens.set(chunk.usage().outputTokens());
                    }
                }
                default -> {
                    // ping, content_block_start, content_block_stop, message_stop — ignored
                }
            }
        } catch (JsonProcessingException e) {
            log.debug("Skipping non-JSON SSE chunk: {}", json);
        }
        return List.of();
    }
}
