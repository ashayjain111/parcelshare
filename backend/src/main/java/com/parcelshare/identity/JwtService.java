package com.parcelshare.identity;

import com.parcelshare.common.AppProps;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final int hours;

    public JwtService(AppProps props) {
        byte[] bytes = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("parcelshare.jwt-secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.hours = props.jwtHours();
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(hours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** Returns the user id if the token is valid and unexpired. */
    public Optional<Long> parse(String token) {
        try {
            String sub = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(Long.parseLong(sub));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
