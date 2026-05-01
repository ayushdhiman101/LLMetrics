package com.llmgateway.provider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RoutingChain {

    private static final Logger log = LoggerFactory.getLogger(RoutingChain.class);

    private final ProviderRouter router;
    private final CircuitBreakerRegistry circuitBreakers;

    public RoutingChain(ProviderRouter router, CircuitBreakerRegistry circuitBreakers) {
        this.router = router;
        this.circuitBreakers = circuitBreakers;
    }

    public record Chosen(String provider, String model) {}

    public Flux<ProviderEvent> stream(List<String> models, ResolvedPromptRequest base, AtomicReference<Chosen> chosen) {
        if (models == null || models.isEmpty()) {
            return Flux.error(new NoProvidersAvailableException("models[] is required"));
        }
        List<Link> links = models.stream().map(this::link).toList();
        return tryFrom(links, 0, base, chosen);
    }

    private Flux<ProviderEvent> tryFrom(List<Link> links, int idx, ResolvedPromptRequest base, AtomicReference<Chosen> chosen) {
        if (idx >= links.size()) {
            return Flux.error(new NoProvidersAvailableException("All providers in the chain failed or were unavailable"));
        }
        Link link = links.get(idx);
        ResolvedPromptRequest req = base.withModel(link.model);

        ProviderEvent attempting = new ProviderEvent.Attempting(link.adapter.providerName(), link.model);

        Flux<ProviderEvent> source = link.adapter.stream(req)
                .transformDeferred(CircuitBreakerOperator.of(
                        circuitBreakers.circuitBreaker(link.adapter.providerName())));

        Flux<ProviderEvent> providerStream = source.switchOnFirst((signal, flux) -> {
            if (signal.isOnError() && shouldFallback(signal.getThrowable())) {
                String reason = errorReason(signal.getThrowable());
                log.warn("Falling back from provider={} model={} reason={}",
                        link.adapter.providerName(), link.model, reason);
                ProviderEvent fallback = new ProviderEvent.Fallback(link.adapter.providerName(), link.model, reason);
                return Flux.just(fallback).concatWith(tryFrom(links, idx + 1, base, chosen));
            }
            chosen.set(new Chosen(link.adapter.providerName(), link.model));
            return flux;
        });

        return Flux.just(attempting).concatWith(providerStream);
    }

    private static String errorReason(Throwable e) {
        if (e instanceof ProviderUnavailableException) return e.getMessage();
        if (e instanceof CallNotPermittedException) return "circuit breaker open";
        if (e instanceof TimeoutException) return "timeout";
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof WebClientResponseException wre) {
                return wre.getStatusCode().value() + " " + wre.getStatusText();
            }
            if (cause instanceof TimeoutException) return "timeout";
            cause = cause.getCause();
        }
        return e.getMessage();
    }

    private Link link(String model) {
        ProviderAdapter adapter = router.route(model);
        return new Link(adapter, model);
    }

    private static boolean shouldFallback(Throwable e) {
        if (e instanceof ProviderUnavailableException) return true;
        if (e instanceof CallNotPermittedException) return true;
        if (e instanceof TimeoutException) return true;
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof WebClientResponseException wre) {
                int code = wre.getStatusCode().value();
                return code == 429 || code >= 500;
            }
            if (cause instanceof TimeoutException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private record Link(ProviderAdapter adapter, String model) {}
}
