package com.llmgateway.repository;

import com.llmgateway.domain.PromptVersion;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PromptVersionRepository extends ReactiveCrudRepository<PromptVersion, UUID> {
    Mono<PromptVersion> findByPromptIdAndVersion(UUID promptId, String version);
    Flux<PromptVersion> findByPromptIdAndIsActiveTrue(UUID promptId);
    Flux<PromptVersion> findByPromptIdOrderByCreatedAtDesc(UUID promptId);
}
