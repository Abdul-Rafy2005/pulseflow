package com.pulseflow.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret";

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtService = new JwtService(jwtEncoder, jwtDecoder, Duration.ofHours(1));
    }

    @Test
    void generateTokenRoundTripsSubjectAndRole() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setEmail("admin@example.com");
        user.setRole(UserRole.ADMIN);

        String token = jwtService.generateToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("admin");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void extractUsernameRejectsInvalidToken() {
        assertThatThrownBy(() -> jwtService.extractUsername("not-a-token"))
                .isInstanceOf(JwtException.class);
    }
}


