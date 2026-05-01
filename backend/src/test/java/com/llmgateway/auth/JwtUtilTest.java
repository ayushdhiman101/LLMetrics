package com.llmgateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-chars-long-ok";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 24);
    }

    @Test
    void generate_returnsThreePartToken() {
        String token = jwtUtil.generate(UUID.randomUUID(), UUID.randomUUID(), "test@example.com");
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void generate_thenParse_recoversAllClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "user@example.com";

        String token = jwtUtil.generate(userId, tenantId, email);
        Claims claims = jwtUtil.parse(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("tenantId", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(email);
    }

    @Test
    void parse_tamperedSignature_throwsJwtException() {
        String token = jwtUtil.generate(UUID.randomUUID(), UUID.randomUUID(), "a@b.com");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThatThrownBy(() -> jwtUtil.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void parse_tokenSignedWithDifferentSecret_throwsJwtException() {
        JwtUtil other = new JwtUtil("completely-different-secret-at-least-32-chars-x", 24);
        String token = other.generate(UUID.randomUUID(), UUID.randomUUID(), "a@b.com");
        assertThatThrownBy(() -> jwtUtil.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void generate_differentUsers_produceDifferentTokens() {
        String t1 = jwtUtil.generate(UUID.randomUUID(), UUID.randomUUID(), "a@b.com");
        String t2 = jwtUtil.generate(UUID.randomUUID(), UUID.randomUUID(), "c@d.com");
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void parse_garbage_throwsJwtException() {
        assertThatThrownBy(() -> jwtUtil.parse("not.a.token")).isInstanceOf(JwtException.class);
    }
}
