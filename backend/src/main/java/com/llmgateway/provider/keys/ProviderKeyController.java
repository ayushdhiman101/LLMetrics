package com.llmgateway.provider.keys;

import com.llmgateway.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/v1/user/provider-keys")
public class ProviderKeyController {

    private final ProviderKeyService providerKeyService;

    public ProviderKeyController(ProviderKeyService providerKeyService) {
        this.providerKeyService = providerKeyService;
    }

    private static Mono<User> resolveUser() {
        return Mono.deferContextual(ctx -> {
            if (!ctx.hasKey("user")) {
                return Mono.error(new UserRequiredException());
            }
            return Mono.just((User) ctx.get("user"));
        });
    }

    @GetMapping
    public Mono<ResponseEntity<List<ProviderKeyService.ProviderKeyResponse>>> list() {
        return resolveUser()
                .flatMapMany(u -> providerKeyService.listKeys(u.id()))
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorResume(UserRequiredException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @PutMapping("/{provider}")
    public Mono<ResponseEntity<Void>> upsert(
            @PathVariable String provider,
            @RequestBody UpsertKeyRequest body) {
        return resolveUser()
                .flatMap(u -> providerKeyService.upsertKey(u.id(), provider, body.apiKey()))
                .map(k -> ResponseEntity.ok().<Void>build())
                .onErrorResume(UserRequiredException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @DeleteMapping("/{provider}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String provider) {
        return resolveUser()
                .flatMap(u -> providerKeyService.deleteKey(u.id(), provider))
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(UserRequiredException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    public record UpsertKeyRequest(String apiKey) {}

    static class UserRequiredException extends RuntimeException {
        UserRequiredException() {
            super("This endpoint requires JWT authentication");
        }
    }
}
