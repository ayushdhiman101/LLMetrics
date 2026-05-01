package com.llmgateway.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthService.AuthResponse>> register(@RequestBody RegisterRequest body) {
        if (body.email() == null || body.email().isBlank()
                || body.password() == null || body.password().length() < 8) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return authService.register(body.email(), body.password())
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r))
                .onErrorResume(AuthService.EmailAlreadyExistsException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build()));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthService.AuthResponse>> login(@RequestBody LoginRequest body) {
        return authService.login(body.email(), body.password())
                .map(ResponseEntity::ok)
                .onErrorResume(AuthService.InvalidCredentialsException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout() {
        // JWT is stateless — the client discards the token; server acknowledges with 200.
        return Mono.just(ResponseEntity.ok().<Void>build());
    }

    public record RegisterRequest(String email, String password) {}
    public record LoginRequest(String email, String password) {}
}
