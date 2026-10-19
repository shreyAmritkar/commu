package com.engish.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(SecurityProperties props) {
        byte[] secret = props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET is missing or shorter than 32 characters. Set it in backend/.env (openssl rand -base64 48).");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expiryMinutes = props.jwtExpiryMinutes() > 0 ? props.jwtExpiryMinutes() : 60;
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Returns the user id if the token is valid, correctly signed and not expired. */
    public Optional<UUID> parseUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}



// generate(User user) → Called after successful login to create a signed JWT containing the user's ID and expiration time.
// parseUserId(String token) → Called on subsequent protected requests to verify the token and extract the user's ID.