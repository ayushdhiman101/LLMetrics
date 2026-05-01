package com.llmgateway.auth;

import com.llmgateway.repository.ApiKeyRepository;
import com.llmgateway.repository.TenantRepository;
import com.llmgateway.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AuthController.class)
class AuthControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityWebFilterChain testFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeExchange(auth -> auth.anyExchange().permitAll())
                    .build();
        }
    }

    @MockBean private AuthService authService;

    // JwtFilter is a @Component loaded by @WebFluxTest — mock its dependencies
    @MockBean private ApiKeyRepository apiKeyRepository;
    @MockBean private TenantRepository tenantRepository;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtUtil jwtUtil;

    @Autowired private WebTestClient webClient;

    private static final AuthService.AuthResponse FAKE_RESPONSE =
            new AuthService.AuthResponse("tok.en.here", UUID.randomUUID(), UUID.randomUUID(), "user@example.com");

    @Test
    void register_validBody_returns201WithToken() {
        when(authService.register("user@example.com", "password123")).thenReturn(Mono.just(FAKE_RESPONSE));

        webClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"password123\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.token").isEqualTo("tok.en.here")
                .jsonPath("$.email").isEqualTo("user@example.com");
    }

    @Test
    void register_shortPassword_returns400() {
        webClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"short\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void register_missingEmail_returns400() {
        webClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"\",\"password\":\"password123\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void register_duplicateEmail_returns409() {
        when(authService.register(any(), any()))
                .thenReturn(Mono.error(new AuthService.EmailAlreadyExistsException("user@example.com")));

        webClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"password123\"}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void login_validCredentials_returns200WithToken() {
        when(authService.login("user@example.com", "password123")).thenReturn(Mono.just(FAKE_RESPONSE));

        webClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"password123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("tok.en.here");
    }

    @Test
    void login_wrongCredentials_returns401() {
        when(authService.login(any(), any()))
                .thenReturn(Mono.error(new AuthService.InvalidCredentialsException()));

        webClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void logout_always200() {
        webClient.post().uri("/auth/logout")
                .exchange()
                .expectStatus().isOk();
    }
}
