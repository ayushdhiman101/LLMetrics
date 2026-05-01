package com.llmgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${providers.openai.base-url}")
    private String openAiBaseUrl;

    @Value("${providers.gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${providers.anthropic.base-url}")
    private String anthropicBaseUrl;

    @Value("${providers.anthropic.version:2023-06-01}")
    private String anthropicVersion;

    @Bean("openAiWebClient")
    public WebClient openAiWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(openAiBaseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean("geminiWebClient")
    public WebClient geminiWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(geminiBaseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean("anthropicWebClient")
    public WebClient anthropicWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(anthropicBaseUrl)
                .defaultHeader("anthropic-version", anthropicVersion)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }
}
