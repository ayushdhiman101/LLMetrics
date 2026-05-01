package com.llmgateway.provider.gemini;

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
public class GeminiAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(GeminiAdapter.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${providers.gemini.default-model:gemini-flash-latest}")
    private String defaultModel;

    public GeminiAdapter(
            @Qualifier("geminiWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public Flux<ProviderEvent> stream(ResolvedPromptRequest request) {
        String model = request.model() != null ? request.model() : defaultModel;

        String apiKey = request.providerKeys() != null
                ? request.providerKeys().getOrDefault("gemini", "") : "";

        return webClient.post()
                .uri("/models/{model}:streamGenerateContent?alt=sse", model)
                .header("X-goog-api-key", apiKey)
                .bodyValue(GeminiRequest.of(request.resolvedContent()))
                .retrieve()
                .bodyToFlux(String.class)
                .map(line -> line.startsWith("data: ") ? line.substring(6) : line)
                .filter(json -> !json.isBlank() && json.startsWith("{"))
                .flatMapIterable(this::parseChunk)
                .filter(Objects::nonNull)
                .onBackpressureBuffer(256)
                .timeout(Duration.ofSeconds(60));
    }

    // Gemini emits usageMetadata on every chunk; the cumulative final value wins via "keep the last" downstream.
    private List<ProviderEvent> parseChunk(String json) {
        List<ProviderEvent> events = new ArrayList<>(2);
        try {
            GeminiResponse response = objectMapper.readValue(json, GeminiResponse.class);
            if (response.candidates() != null && !response.candidates().isEmpty()) {
                GeminiResponse.Content content = response.candidates().get(0).content();
                if (content != null && content.parts() != null && !content.parts().isEmpty()) {
                    String text = content.parts().get(0).text();
                    if (text != null && !text.isEmpty()) {
                        events.add(new ProviderEvent.Token(text));
                    }
                }
            }
            if (response.usageMetadata() != null) {
                events.add(new ProviderEvent.Usage(
                        response.usageMetadata().promptTokenCount(),
                        response.usageMetadata().candidatesTokenCount()));
            }
        } catch (JsonProcessingException e) {
            log.debug("Skipping non-JSON SSE chunk: {}", json);
        }
        return events;
    }
}
