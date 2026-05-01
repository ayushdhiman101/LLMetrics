package com.llmgateway.auth;

import com.llmgateway.domain.Tenant;
import com.llmgateway.domain.User;
import com.llmgateway.repository.TenantRepository;
import com.llmgateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtUtil jwtUtil;

    private AuthService authService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // low cost for test speed
        authService = new AuthService(userRepository, tenantRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void register_newEmail_createsUserAndReturnsToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "new@example.com";
        String fakeToken = "jwt.token.here";

        Tenant savedTenant = new Tenant(tenantId, "new", LocalDateTime.now());
        User savedUser = new User(userId, tenantId, email, "hash", LocalDateTime.now());

        when(userRepository.findByEmail(email)).thenReturn(Mono.empty());
        when(tenantRepository.save(any(Tenant.class))).thenReturn(Mono.just(savedTenant));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(savedUser));
        when(jwtUtil.generate(userId, tenantId, email)).thenReturn(fakeToken);

        StepVerifier.create(authService.register(email, "password123"))
                .assertNext(r -> {
                    assertThat(r.token()).isEqualTo(fakeToken);
                    assertThat(r.email()).isEqualTo(email);
                    assertThat(r.userId()).isEqualTo(userId);
                    assertThat(r.tenantId()).isEqualTo(tenantId);
                })
                .verifyComplete();
    }

    @Test
    void register_duplicateEmail_emitsEmailAlreadyExistsException() {
        String email = "existing@example.com";
        User existing = new User(UUID.randomUUID(), UUID.randomUUID(), email, "hash", LocalDateTime.now());
        when(userRepository.findByEmail(email)).thenReturn(Mono.just(existing));

        StepVerifier.create(authService.register(email, "password123"))
                .expectError(AuthService.EmailAlreadyExistsException.class)
                .verify();
    }

    @Test
    void register_tenantNameIsDerivedFromEmailLocalPart() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "alice@example.com";

        when(userRepository.findByEmail(email)).thenReturn(Mono.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            assertThat(t.name()).isEqualTo("alice");
            return Mono.just(new Tenant(tenantId, t.name(), LocalDateTime.now()));
        });
        when(userRepository.save(any(User.class))).thenReturn(
                Mono.just(new User(userId, tenantId, email, "h", LocalDateTime.now())));
        when(jwtUtil.generate(any(), any(), any())).thenReturn("tok");

        StepVerifier.create(authService.register(email, "password123"))
                .assertNext(r -> assertThat(r.email()).isEqualTo(email))
                .verifyComplete();
    }

    @Test
    void login_validCredentials_returnsToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "user@example.com";
        String rawPassword = "password123";
        String fakeToken = "jwt.token";

        User user = new User(userId, tenantId, email, passwordEncoder.encode(rawPassword), LocalDateTime.now());
        when(userRepository.findByEmail(email)).thenReturn(Mono.just(user));
        when(jwtUtil.generate(userId, tenantId, email)).thenReturn(fakeToken);

        StepVerifier.create(authService.login(email, rawPassword))
                .assertNext(r -> {
                    assertThat(r.token()).isEqualTo(fakeToken);
                    assertThat(r.email()).isEqualTo(email);
                    assertThat(r.userId()).isEqualTo(userId);
                })
                .verifyComplete();
    }

    @Test
    void login_wrongPassword_emitsInvalidCredentialsException() {
        String email = "user@example.com";
        User user = new User(UUID.randomUUID(), UUID.randomUUID(), email,
                passwordEncoder.encode("correct-password"), LocalDateTime.now());
        when(userRepository.findByEmail(email)).thenReturn(Mono.just(user));

        StepVerifier.create(authService.login(email, "wrong-password"))
                .expectError(AuthService.InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void login_unknownEmail_emitsInvalidCredentialsException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(authService.login("nobody@example.com", "any-password"))
                .expectError(AuthService.InvalidCredentialsException.class)
                .verify();
    }
}
