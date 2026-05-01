package com.llmgateway.provider;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoutingChainTest {

    @Mock private ProviderRouter router;
    @Mock private ProviderAdapter primaryAdapter;
    @Mock private ProviderAdapter fallbackAdapter;

    private RoutingChain chain;
    private ResolvedPromptRequest base;

    @BeforeEach
    void setUp() {
        chain = new RoutingChain(router, CircuitBreakerRegistry.ofDefaults());
        base = new ResolvedPromptRequest(UUID.randomUUID(), "hello", "v1", "Say hi", null, null, null);

        when(primaryAdapter.providerName()).thenReturn("openai");
        when(fallbackAdapter.providerName()).thenReturn("gemini");
        when(router.route("gpt-4o-mini")).thenReturn(primaryAdapter);
        when(router.route("gemini-flash")).thenReturn(fallbackAdapter);
    }

    @Test
    void happyPath_returnsAllEventsAndSetsChosen() {
        ProviderEvent.Token t1 = new ProviderEvent.Token("Hello");
        ProviderEvent.Token t2 = new ProviderEvent.Token(" world");
        ProviderEvent.Usage usage = new ProviderEvent.Usage(10, 20);
        when(primaryAdapter.stream(any())).thenReturn(Flux.just(t1, t2, usage));

        AtomicReference<RoutingChain.Chosen> chosen = new AtomicReference<>();
        StepVerifier.create(chain.stream(List.of("gpt-4o-mini"), base, chosen))
                .expectNext(new ProviderEvent.Attempting("openai", "gpt-4o-mini"))
                .expectNext(t1, t2, usage)
                .verifyComplete();

        assertThat(chosen.get().provider()).isEqualTo("openai");
        assertThat(chosen.get().model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void rateLimitError_fallsBackToSecondProvider() {
        WebClientResponseException tooManyRequests = WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        when(primaryAdapter.stream(any())).thenReturn(Flux.error(tooManyRequests));

        ProviderEvent.Token token = new ProviderEvent.Token("Fallback response");
        when(fallbackAdapter.stream(any())).thenReturn(Flux.just(token));

        AtomicReference<RoutingChain.Chosen> chosen = new AtomicReference<>();
        StepVerifier.create(chain.stream(List.of("gpt-4o-mini", "gemini-flash"), base, chosen))
                .expectNext(new ProviderEvent.Attempting("openai", "gpt-4o-mini"))
                .expectNextMatches(e -> e instanceof ProviderEvent.Fallback f && f.provider().equals("openai"))
                .expectNext(new ProviderEvent.Attempting("gemini", "gemini-flash"))
                .expectNext(token)
                .verifyComplete();

        assertThat(chosen.get().provider()).isEqualTo("gemini");
    }

    @Test
    void serverError_fallsBackToSecondProvider() {
        WebClientResponseException serverError = WebClientResponseException.create(
                503, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
        when(primaryAdapter.stream(any())).thenReturn(Flux.error(serverError));

        ProviderEvent.Token token = new ProviderEvent.Token("ok");
        when(fallbackAdapter.stream(any())).thenReturn(Flux.just(token));

        AtomicReference<RoutingChain.Chosen> chosen = new AtomicReference<>();
        StepVerifier.create(chain.stream(List.of("gpt-4o-mini", "gemini-flash"), base, chosen))
                .expectNext(new ProviderEvent.Attempting("openai", "gpt-4o-mini"))
                .expectNextMatches(e -> e instanceof ProviderEvent.Fallback)
                .expectNext(new ProviderEvent.Attempting("gemini", "gemini-flash"))
                .expectNext(token)
                .verifyComplete();

        assertThat(chosen.get().provider()).isEqualTo("gemini");
    }

    @Test
    void allProvidersFail_emitsNoProvidersAvailableException() {
        WebClientResponseException err = WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        when(primaryAdapter.stream(any())).thenReturn(Flux.error(err));
        when(fallbackAdapter.stream(any())).thenReturn(Flux.error(err));

        StepVerifier.create(chain.stream(List.of("gpt-4o-mini", "gemini-flash"), base, new AtomicReference<>()))
                .expectNextMatches(e -> e instanceof ProviderEvent.Attempting)
                .expectNextMatches(e -> e instanceof ProviderEvent.Fallback)
                .expectNextMatches(e -> e instanceof ProviderEvent.Attempting)
                .expectNextMatches(e -> e instanceof ProviderEvent.Fallback)
                .expectError(NoProvidersAvailableException.class)
                .verify();
    }

    @Test
    void emptyModelList_emitsNoProvidersAvailableException() {
        StepVerifier.create(chain.stream(List.of(), base, new AtomicReference<>()))
                .expectError(NoProvidersAvailableException.class)
                .verify();
    }

    @Test
    void nonFallbackError_propagatesWithoutAdvancing() {
        // 400 Bad Request should NOT trigger fallback
        WebClientResponseException badRequest = WebClientResponseException.create(
                400, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
        when(primaryAdapter.stream(any())).thenReturn(Flux.error(badRequest));

        AtomicReference<RoutingChain.Chosen> chosen = new AtomicReference<>();
        StepVerifier.create(chain.stream(List.of("gpt-4o-mini", "gemini-flash"), base, chosen))
                .expectNextMatches(e -> e instanceof ProviderEvent.Attempting)
                .expectError(WebClientResponseException.class)
                .verify();

        // chosen was committed when the first non-fallback signal arrived (it was a 400 error,
        // not a fallback error, so the chain committed to openai before propagating the error)
        assertThat(chosen.get()).isNotNull();
        assertThat(chosen.get().provider()).isEqualTo("openai");
    }

    @Test
    void circuitBreakerOpen_fallsBackToSecondProvider() {
        // Force the primary provider's circuit breaker into OPEN state
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("openai").transitionToOpenState();

        chain = new RoutingChain(router, registry);

        // stream() is called to get the Flux object; the CB operator intercepts at subscription time.
        when(primaryAdapter.stream(any())).thenReturn(Flux.never());

        ProviderEvent.Token token = new ProviderEvent.Token("from fallback");
        when(fallbackAdapter.stream(any())).thenReturn(Flux.just(token));

        AtomicReference<RoutingChain.Chosen> chosen = new AtomicReference<>();
        StepVerifier.create(chain.stream(List.of("gpt-4o-mini", "gemini-flash"), base, chosen))
                .expectNextMatches(e -> e instanceof ProviderEvent.Attempting)
                .expectNextMatches(e -> e instanceof ProviderEvent.Fallback)
                .expectNextMatches(e -> e instanceof ProviderEvent.Attempting)
                .expectNext(token)
                .verifyComplete();

        assertThat(chosen.get().provider()).isEqualTo("gemini");
    }
}
