package com.llmgateway.auth;

import com.llmgateway.repository.ApiKeyRepository;
import com.llmgateway.repository.TenantRepository;
import com.llmgateway.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
@Order(-100)
public class JwtFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public JwtFilter(ApiKeyRepository apiKeyRepository,
                     TenantRepository tenantRepository,
                     UserRepository userRepository,
                     JwtUtil jwtUtil) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/v1/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return handleJwt(authHeader.substring(BEARER_PREFIX.length()), exchange, chain);
        }

        String rawKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        if (rawKey != null && !rawKey.isBlank()) {
            return handleApiKey(rawKey, exchange, chain);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> handleJwt(String token, ServerWebExchange exchange, WebFilterChain chain) {
        try {
            Claims claims = jwtUtil.parse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));

            return userRepository.findById(userId)
                    .zipWith(tenantRepository.findById(tenantId))
                    .flatMap(tuple -> chain.filter(exchange)
                            .contextWrite(ctx -> ctx
                                    .put("tenant", tuple.getT2())
                                    .put("user", tuple.getT1())))
                    .switchIfEmpty(unauthorized(exchange));
        } catch (JwtException | IllegalArgumentException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> handleApiKey(String rawKey, ServerWebExchange exchange, WebFilterChain chain) {
        String keyHash = sha256Hex(rawKey);
        return apiKeyRepository.findByKeyHash(keyHash)
                .flatMap(apiKey -> tenantRepository.findById(apiKey.tenantId()))
                .flatMap(tenant -> chain.filter(exchange)
                        .contextWrite(ctx -> ctx.put("tenant", tenant)))
                .switchIfEmpty(unauthorized(exchange));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        });
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
