package com.llmgateway.provider;

public sealed interface ProviderEvent permits ProviderEvent.Token, ProviderEvent.Usage, ProviderEvent.Attempting, ProviderEvent.Fallback {

    record Token(String content) implements ProviderEvent {}

    record Usage(int inputTokens, int outputTokens) implements ProviderEvent {}

    record Attempting(String provider, String model) implements ProviderEvent {}

    record Fallback(String provider, String model, String reason) implements ProviderEvent {}
}
