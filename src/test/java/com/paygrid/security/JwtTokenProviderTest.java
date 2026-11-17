package com.paygrid.security;

import com.paygrid.entity.Role;
import com.paygrid.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, expirationMs);
    }

    @Test
    @DisplayName("Should generate a valid JWT token and parse claims correctly")
    void generateToken_And_ParseClaims() {
        User user = new User("sarah", "sarah@example.com", "secret", Role.ROLE_USER, "PG-SARAH001", BigDecimal.ZERO);
        user.setId(42L);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = jwtTokenProvider.generateToken(auth);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("sarah");
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("Should return false for malformed JWT token")
    void validateToken_MalformedToken_ReturnsFalse() {
        assertThat(jwtTokenProvider.validateToken("invalid.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("Should return false for expired JWT token")
    void validateToken_ExpiredToken_ReturnsFalse() {
        // Expiration ms = -1000 (already expired)
        JwtTokenProvider expiredProvider = new JwtTokenProvider(secret, -1000);
        String expiredToken = expiredProvider.generateTokenFromUsername("expired_user", 1L, "ROLE_USER");

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }
}

