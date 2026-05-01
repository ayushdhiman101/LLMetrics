package com.llmgateway.provider.keys;

import com.llmgateway.auth.EncryptionService;
import com.llmgateway.domain.UserProviderKey;
import com.llmgateway.repository.UserProviderKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProviderKeyService {

    private final UserProviderKeyRepository keyRepository;
    private final EncryptionService encryptionService;

    @Value("${providers.openai.api-key:}")
    private String globalOpenAiKey;

    @Value("${providers.gemini.api-key:}")
    private String globalGeminiKey;

    @Value("${providers.anthropic.api-key:}")
    private String globalAnthropicKey;

    public ProviderKeyService(UserProviderKeyRepository keyRepository,
                              EncryptionService encryptionService) {
        this.keyRepository = keyRepository;
        this.encryptionService = encryptionService;
    }

    /** Resolves keys for a JWT-authenticated user; fills missing providers from global env-var fallback. */
    public Mono<Map<String, String>> buildProviderKeyMap(UUID userId) {
        return keyRepository.findByUserId(userId)
                .collectMap(
                        UserProviderKey::provider,
                        k -> encryptionService.decrypt(k.encryptedKey()))
                .map(userKeys -> {
                    Map<String, String> result = new HashMap<>(userKeys);
                    result.putIfAbsent("openai", globalOpenAiKey);
                    result.putIfAbsent("gemini", globalGeminiKey);
                    result.putIfAbsent("anthropic", globalAnthropicKey);
                    return result;
                });
    }

    /** Returns only the global env-var keys — used when the request is authenticated via X-API-Key (no user context). */
    public Mono<Map<String, String>> buildGlobalKeyMap() {
        Map<String, String> keys = new HashMap<>();
        keys.put("openai", globalOpenAiKey);
        keys.put("gemini", globalGeminiKey);
        keys.put("anthropic", globalAnthropicKey);
        return Mono.just(keys);
    }

    public Mono<UserProviderKey> upsertKey(UUID userId, String provider, String plainKey) {
        String encrypted = encryptionService.encrypt(plainKey);
        return keyRepository.findByUserIdAndProvider(userId, provider)
                .flatMap(existing -> keyRepository.save(
                        new UserProviderKey(existing.id(), userId, provider, encrypted,
                                existing.createdAt(), LocalDateTime.now())))
                .switchIfEmpty(keyRepository.save(
                        new UserProviderKey(null, userId, provider, encrypted,
                                LocalDateTime.now(), LocalDateTime.now())));
    }

    public Mono<Void> deleteKey(UUID userId, String provider) {
        return keyRepository.deleteByUserIdAndProvider(userId, provider);
    }

    public Flux<ProviderKeyResponse> listKeys(UUID userId) {
        return keyRepository.findByUserId(userId)
                .map(k -> new ProviderKeyResponse(k.provider(), true, k.updatedAt()));
    }

    public record ProviderKeyResponse(String provider, boolean configured, LocalDateTime updatedAt) {}
}
