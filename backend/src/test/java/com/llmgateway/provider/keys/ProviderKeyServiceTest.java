package com.llmgateway.provider.keys;

import com.llmgateway.auth.EncryptionService;
import com.llmgateway.domain.UserProviderKey;
import com.llmgateway.repository.UserProviderKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderKeyServiceTest {

    @Mock private UserProviderKeyRepository keyRepository;
    @Mock private EncryptionService encryptionService;

    private ProviderKeyService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderKeyService(keyRepository, encryptionService);
    }

    @Test
    void upsertKey_newProvider_savesEncryptedKey() {
        String plainKey = "sk-test-key";
        String encrypted = "base64encrypted";
        UserProviderKey saved = new UserProviderKey(UUID.randomUUID(), userId, "openai", encrypted,
                LocalDateTime.now(), LocalDateTime.now());

        when(encryptionService.encrypt(plainKey)).thenReturn(encrypted);
        when(keyRepository.findByUserIdAndProvider(userId, "openai")).thenReturn(Mono.empty());
        when(keyRepository.save(any(UserProviderKey.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.upsertKey(userId, "openai", plainKey))
                .assertNext(k -> {
                    assertThat(k.provider()).isEqualTo("openai");
                    assertThat(k.encryptedKey()).isEqualTo(encrypted);
                })
                .verifyComplete();

        verify(encryptionService).encrypt(plainKey);
    }

    @Test
    void upsertKey_existingProvider_updatesRecord() {
        String newPlainKey = "sk-new-key";
        String newEncrypted = "base64new";
        UUID existingId = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.now().minusDays(1);
        UserProviderKey existing = new UserProviderKey(existingId, userId, "openai", "base64old", created, created);
        UserProviderKey updated = new UserProviderKey(existingId, userId, "openai", newEncrypted, created, LocalDateTime.now());

        when(encryptionService.encrypt(newPlainKey)).thenReturn(newEncrypted);
        when(keyRepository.findByUserIdAndProvider(userId, "openai")).thenReturn(Mono.just(existing));
        when(keyRepository.save(any(UserProviderKey.class))).thenReturn(Mono.just(updated));

        StepVerifier.create(service.upsertKey(userId, "openai", newPlainKey))
                .assertNext(k -> {
                    assertThat(k.id()).isEqualTo(existingId);
                    assertThat(k.encryptedKey()).isEqualTo(newEncrypted);
                })
                .verifyComplete();
    }

    @Test
    void deleteKey_delegatesToRepository() {
        when(keyRepository.deleteByUserIdAndProvider(userId, "openai")).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteKey(userId, "openai"))
                .verifyComplete();

        verify(keyRepository).deleteByUserIdAndProvider(userId, "openai");
    }

    @Test
    void buildProviderKeyMap_decryptsUserKey_andIncludesMissingProviders() {
        UserProviderKey openaiKey = new UserProviderKey(UUID.randomUUID(), userId, "openai", "enc",
                LocalDateTime.now(), LocalDateTime.now());
        when(keyRepository.findByUserId(userId)).thenReturn(Flux.just(openaiKey));
        when(encryptionService.decrypt("enc")).thenReturn("sk-user-openai");

        StepVerifier.create(service.buildProviderKeyMap(userId))
                .assertNext(map -> {
                    assertThat(map.get("openai")).isEqualTo("sk-user-openai");
                    // global keys default to empty string via @Value with empty default
                    assertThat(map).containsKeys("openai", "gemini", "anthropic");
                })
                .verifyComplete();
    }

    @Test
    void buildProviderKeyMap_noUserKeys_returnsGlobalDefaults() {
        when(keyRepository.findByUserId(userId)).thenReturn(Flux.empty());

        StepVerifier.create(service.buildProviderKeyMap(userId))
                .assertNext(map -> assertThat(map).containsKeys("openai", "gemini", "anthropic"))
                .verifyComplete();
    }

    @Test
    void buildGlobalKeyMap_alwaysContainsThreeProviders() {
        StepVerifier.create(service.buildGlobalKeyMap())
                .assertNext(map -> assertThat(map).containsKeys("openai", "gemini", "anthropic"))
                .verifyComplete();
    }

    @Test
    void listKeys_mapsEachKeyToProviderKeyResponse() {
        UserProviderKey geminiKey = new UserProviderKey(UUID.randomUUID(), userId, "gemini", "enc",
                LocalDateTime.now(), LocalDateTime.now());
        UserProviderKey openaiKey = new UserProviderKey(UUID.randomUUID(), userId, "openai", "enc2",
                LocalDateTime.now(), LocalDateTime.now());
        when(keyRepository.findByUserId(userId)).thenReturn(Flux.just(geminiKey, openaiKey));

        StepVerifier.create(service.listKeys(userId).collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list).allMatch(ProviderKeyService.ProviderKeyResponse::configured);
                    assertThat(list).extracting(ProviderKeyService.ProviderKeyResponse::provider)
                            .containsExactlyInAnyOrder("gemini", "openai");
                })
                .verifyComplete();
    }
}
