package com.llmgateway.gateway;

import com.llmgateway.domain.Tenant;
import com.llmgateway.dto.CompletionRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> complete(@RequestBody CompletionRequest request) {
        return Mono.deferContextual(ctx -> {
                    Tenant tenant = ctx.get("tenant");
                    return Mono.just(gatewayService.process(tenant, request));
                })
                .flatMapMany(f -> f);
    }
}
