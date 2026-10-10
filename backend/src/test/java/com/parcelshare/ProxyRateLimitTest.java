package com.parcelshare;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** Behind a host's proxy, each real visitor (X-Forwarded-For) must get their own rate-limit bucket. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:proxy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${TEST_DB_USER:sa}",
                "spring.datasource.password=${TEST_DB_PASSWORD:}",
                "parcelshare.allow-dev-defaults=true",
                "parcelshare.rate-limit-per-minute=3"
        })
class ProxyRateLimitTest {

    @Autowired
    TestRestTemplate http;

    private HttpStatus loginAttemptFrom(String clientIp) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Forwarded-For", clientIp);
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return HttpStatus.valueOf(http.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "nobody@x.test", "password", "wrong-pass"), h), String.class).getStatusCode().value());
    }

    @Test
    void oneNoisyVisitorDoesNotBlockEveryoneElse() {
        for (int i = 0; i < 3; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, loginAttemptFrom("203.0.113.10")); // wrong password, but not rate limited yet
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, loginAttemptFrom("203.0.113.10"));
        assertEquals(HttpStatus.UNAUTHORIZED, loginAttemptFrom("198.51.100.77"), "a different visitor has a fresh allowance");
    }
}
