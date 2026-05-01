package com.llmgateway.repository;

import com.llmgateway.domain.UserProviderKey;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserProviderKeyRepository extends ReactiveCrudRepository<UserProviderKey, UUID> {
    Flux<UserProviderKey> findByUserId(UUID userId);
    Mono<UserProviderKey> findByUserIdAndProvider(UUID userId, String provider);

    @Modifying
    @Query("DELETE FROM user_provider_keys WHERE user_id = :userId AND provider = :provider")
    Mono<Void> deleteByUserIdAndProvider(UUID userId, String provider);
}
