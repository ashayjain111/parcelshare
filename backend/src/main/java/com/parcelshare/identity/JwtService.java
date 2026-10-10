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
                .claim("tv", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(hours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public record Parsed(long userId, int tokenVersion) {
    }

    /** Returns who the token belongs to (and which token generation it is) if it is valid and unexpired. */
    public Optional<Parsed> parse(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Integer tv = claims.get("tv", Integer.class);
            return Optional.of(new Parsed(Long.parseLong(claims.getSubject()), tv == null ? 0 : tv));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
