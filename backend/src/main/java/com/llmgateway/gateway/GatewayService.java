package com.llmgateway.gateway;

import com.llmgateway.domain.Tenant;
import com.llmgateway.domain.User;
import com.llmgateway.dto.CompletionRequest;
import com.llmgateway.metrics.MetricsPipeline;
import com.llmgateway.prompt.PromptService;
import com.llmgateway.provider.ProviderEvent;
import com.llmgateway.provider.ResolvedPromptRequest;
import com.llmgateway.provider.RoutingChain;
import com.llmgateway.provider.keys.ProviderKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final PromptService promptService;
    private final RoutingChain routingChain;
    private final MetricsPipeline metricsPipeline;
    private final ProviderKeyService providerKeyService;

    public GatewayService(PromptService promptService,
                          RoutingChain routingChain,
                          MetricsPipeline metricsPipeline,
                          ProviderKeyService providerKeyService) {
        this.promptService = promptService;
        this.routingChain = routingChain;
        this.metricsPipeline = metricsPipeline;
        this.providerKeyService = providerKeyService;
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    public Flux<ServerSentEvent<String>> process(Tenant tenant, CompletionRequest request) {
        long startTime = System.currentTimeMillis();

        Mono<Map<String, String>> keysMono = Mono.deferContextual(ctx -> {
            if (ctx.hasKey("user")) {
                User user = ctx.get("user");
                return providerKeyService.buildProviderKeyMap(user.id());
            }
            return providerKeyService.buildGlobalKeyMap();
        });

        boolean isRaw = request.message() != null && !request.message().isBlank();
        Mono<PromptService.ResolvedPrompt> resolveMono = isRaw
                ? Mono.just(new PromptService.ResolvedPrompt(null, null, request.message()))
                : promptService.resolve(tenant.id(), request.promptName(), request.version(), request.variables());

        return Mono.zip(resolveMono, keysMono)
                .flatMapMany(tuple -> {
                    PromptService.ResolvedPrompt resolved = tuple.getT1();
                    Map<String, String> keys = tuple.getT2();

                    ResolvedPromptRequest base = new ResolvedPromptRequest(
                            tenant.id(),
                            request.promptName(),
                            resolved.version(),
                            resolved.content(),
                            null,
                            request.strategy(),
                            keys
                    );

                    AtomicReference<RoutingChain.Chosen> chosenRef = new AtomicReference<>();
                    AtomicReference<ProviderEvent.Usage> usageRef = new AtomicReference<>();

                    Flux<ServerSentEvent<String>> sseFlux = routingChain.stream(request.models(), base, chosenRef)
                            .doOnNext(event -> {
                                if (event instanceof ProviderEvent.Usage u) usageRef.set(u);
                            })
                            .<ServerSentEvent<String>>flatMap(event -> switch (event) {
                                case ProviderEvent.Token t ->
                                        Mono.just(sse("token", t.content()));
                                case ProviderEvent.Attempting a ->
                                        Mono.just(sse("attempting", a.provider() + "/" + a.model()));
                                case ProviderEvent.Fallback f ->
                                        Mono.just(sse("fallback", f.provider() + "/" + f.model() + ": " + f.reason()));
                                case ProviderEvent.Usage ignored -> Mono.empty();
                            })
                            .doOnComplete(() -> {
                                long latency = System.currentTimeMillis() - startTime;
                                RoutingChain.Chosen chosen = chosenRef.get();
                                if (chosen == null) {
                                    log.warn("Stream completed but no provider was chosen — skipping metrics");
                                    return;
                                }
                                ProviderEvent.Usage usage = usageRef.get();
                                metricsPipeline.record(
                                        tenant.id(),
                                        resolved.promptId(),
                                        resolved.version(),
                                        chosen.provider(),
                                        chosen.model(),
                                        usage != null ? usage.inputTokens() : 0,
                                        usage != null ? usage.outputTokens() : 0,
                                        latency);
                            })
                            .doOnError(e -> log.error("Stream error for tenant={}", tenant.id(), e));

                    Mono<ServerSentEvent<String>> doneEvent = Mono.defer(() -> {
                        RoutingChain.Chosen chosen = chosenRef.get();
                        return chosen != null
                                ? Mono.just(sse("done", chosen.provider() + "/" + chosen.model()))
                                : Mono.empty();
                    });

                    return sseFlux.concatWith(doneEvent);
                })
                .onErrorResume(e -> {
                    log.error("Gateway error", e);
                    return Flux.just(sse("error", e.getMessage()));
                });
    }
}
