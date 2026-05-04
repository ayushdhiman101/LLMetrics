package com.llmgateway.repository;

import com.llmgateway.domain.Session;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SessionRepository extends ReactiveCrudRepository<Session, UUID> {
    Flux<Session> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
    Mono<Session> findByTenantIdAndEndedAtIsNull(UUID tenantId);
    Mono<Session> findByIdAndTenantId(UUID id, UUID tenantId);
}
