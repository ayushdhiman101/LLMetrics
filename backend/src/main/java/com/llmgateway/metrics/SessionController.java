package com.llmgateway.metrics;

import com.llmgateway.domain.Tenant;
import com.llmgateway.dto.session.RenameSessionRequest;
import com.llmgateway.dto.session.SessionResponse;
import com.llmgateway.dto.session.StartSessionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public Mono<SessionResponse> start(@RequestBody(required = false) StartSessionRequest body) {
        String name = body != null ? body.name() : null;
        return tenant().flatMap(t -> sessionService.startSession(t.id(), name));
    }

    @PostMapping("/{id}/stop")
    public Mono<SessionResponse> stop(@PathVariable UUID id) {
        return tenant().flatMap(t -> sessionService.stopSession(t.id(), id));
    }

    @PutMapping("/{id}/name")
    public Mono<SessionResponse> rename(@PathVariable UUID id, @RequestBody RenameSessionRequest body) {
        return tenant().flatMap(t -> sessionService.renameSession(t.id(), id, body.name()));
    }

    @GetMapping
    public Flux<SessionResponse> list() {
        return tenant().flatMapMany(t -> sessionService.listSessions(t.id()));
    }

    @GetMapping("/active")
    public Mono<ResponseEntity<SessionResponse>> active() {
        return tenant().flatMap(t -> sessionService.getActiveSession(t.id())
                .map(s -> ResponseEntity.ok(s))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NO_CONTENT).build()));
    }

    private static Mono<Tenant> tenant() {
        return Mono.deferContextual(ctx -> Mono.just(ctx.get("tenant")));
    }
}
