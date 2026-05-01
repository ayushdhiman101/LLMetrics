package com.llmgateway.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.provider.ProviderAdapter;
import com.llmgateway.provider.ProviderEvent;
import com.llmgateway.provider.ResolvedPromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class OpenAIAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAdapter.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${providers.openai.default-model:gpt-4o}")
    private String defaultModel;

    public OpenAIAdapter(
            @Qualifier("openAiWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public Flux<ProviderEvent> stream(ResolvedPromptRequest request) {
        String model = request.model() != null ? request.model() : defaultModel;

        OpenAIChatRequest chatRequest = new OpenAIChatRequest(
                model,
                List.of(new OpenAIChatRequest.Message("user", request.resolvedContent())),
                true,
                new OpenAIChatRequest.StreamOptions(true)
        );

        String apiKey = request.providerKeys() != null
                ? request.providerKeys().getOrDefault("openai", "") : "";

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(chatRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.contains("[DONE]"))
                .flatMapIterable(this::parseChunk)
                .filter(Objects::nonNull)
                .onBackpressureBuffer(256)
                .timeout(Duration.ofSeconds(60));
    }

    // OpenAI's final chunk has empty `choices` and a populated `usage` block; earlier chunks are the inverse.
    private List<ProviderEvent> parseChunk(String raw) {
        String json = raw.startsWith("data: ") ? raw.substring(6) : raw;
        List<ProviderEvent> events = new ArrayList<>(2);
        try {
            OpenAIChatResponse response = objectMapper.readValue(json, OpenAIChatResponse.class);
            if (response.choices() != null && !response.choices().isEmpty()) {
                OpenAIChatResponse.Delta delta = response.choices().get(0).delta();
                if (delta != null && delta.content() != null && !delta.content().isEmpty()) {
                    events.add(new ProviderEvent.Token(delta.content()));
                }
            }
            if (response.usage() != null) {
                events.add(new ProviderEvent.Usage(
                        response.usage().promptTokens(),
                        response.usage().completionTokens()));
            }
        } catch (JsonProcessingException e) {
            log.debug("Skipping non-JSON SSE chunk: {}", raw);
        }
        return events;
    }
}
