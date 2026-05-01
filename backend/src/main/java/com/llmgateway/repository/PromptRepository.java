package com.llmgateway.repository;

import com.llmgateway.domain.Prompt;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PromptRepository extends ReactiveCrudRepository<Prompt, UUID> {
    Mono<Prompt> findByTenantIdAndName(UUID tenantId, String name);
    Flux<Prompt> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
