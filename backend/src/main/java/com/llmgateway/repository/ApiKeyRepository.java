package com.llmgateway.repository;

import com.llmgateway.domain.ApiKey;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ApiKeyRepository extends ReactiveCrudRepository<ApiKey, UUID> {
    Mono<ApiKey> findByKeyHash(String keyHash);
}
