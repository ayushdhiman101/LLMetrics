package com.llmgateway.auth;

import com.llmgateway.domain.Tenant;
import com.llmgateway.domain.User;
import com.llmgateway.repository.TenantRepository;
import com.llmgateway.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Mono<AuthResponse> register(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .flatMap(existing -> Mono.<AuthResponse>error(new EmailAlreadyExistsException(email)))
                .switchIfEmpty(Mono.defer(() -> {
                    String tenantName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
                    Tenant newTenant = new Tenant(null, tenantName, LocalDateTime.now());
                    return tenantRepository.save(newTenant)
                            .flatMap(savedTenant -> {
                                String hash = passwordEncoder.encode(rawPassword);
                                User newUser = new User(null, savedTenant.id(), email, hash, LocalDateTime.now());
                                return userRepository.save(newUser)
                                        .map(savedUser -> {
                                            String token = jwtUtil.generate(savedUser.id(), savedTenant.id(), email);
                                            return new AuthResponse(token, savedUser.id(), savedTenant.id(), email);
                                        });
                            });
                }));
    }

    public Mono<AuthResponse> login(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
                        return Mono.error(new InvalidCredentialsException());
                    }
                    String token = jwtUtil.generate(user.id(), user.tenantId(), email);
                    return Mono.just(new AuthResponse(token, user.id(), user.tenantId(), email));
                });
    }

    public record AuthResponse(String token, UUID userId, UUID tenantId, String email) {}

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String email) {
            super("Email already registered: " + email);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }
}
