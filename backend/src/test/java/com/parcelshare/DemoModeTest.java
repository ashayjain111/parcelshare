package com.parcelshare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The demo "simulate next step" endpoint must reuse the real rules and exist only when demo mode is on. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${TEST_DB_USER:sa}",
                "spring.datasource.password=${TEST_DB_PASSWORD:}",
                "parcelshare.demo-mode=true",
                "parcelshare.rate-limit-per-minute=100000",
                "parcelshare.release-window-hours=48",
                "parcelshare.admin-password=TestAdmin123!"
        })
class DemoModeTest {

    @Autowired
    TestRestTemplate http;

    static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    ResponseEntity<Map<String, Object>> call(String token, HttpMethod m, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        return http.exchange(path, m, new HttpEntity<>(body, h), MAP);
    }

    record Acct(long id, String token) {
    }

    Acct register(String name) {
        var r = call(null, HttpMethod.POST, "/auth/register", Map.of("email", name + UUID.randomUUID() + "@t.local",
                "password", "password123", "fullName", name));
        @SuppressWarnings("unchecked")
        var u = (Map<String, Object>) r.getBody().get("user");
        return new Acct(((Number) u.get("id")).longValue(), (String) r.getBody().get("token"));
    }

    long id(String listPath, String token, String name) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange(listPath, HttpMethod.GET, new HttpEntity<>(h), LIST).getBody().stream()
                .filter(c -> name.equals(c.get("name"))).map(c -> ((Number) c.get("id")).longValue()).findFirst().orElseThrow();
    }

    @Test
    void advanceWalksAWholeBookingToReleasedPaymentUsingRealRules() {
        var login = call(null, HttpMethod.POST, "/auth/login", Map.of("email", "admin@parcelshare.local", "password", "TestAdmin123!"));
        String admin = (String) login.getBody().get("token");
        Acct traveller = register("Trav");
        call(traveller.token, HttpMethod.POST, "/me/verification", Map.of("note", "ok"));
        call(admin, HttpMethod.POST, "/admin/verifications/" + traveller.id + "/decision", Map.of("approve", true));
        Acct sender = register("Send");

        long delhi = id("/cities", admin, "Delhi");
        long lucknow = id("/cities", admin, "Lucknow");
        long docs = id("/parcels/categories", admin, "Documents");
        long tripId = ((Number) call(traveller.token, HttpMethod.POST, "/trips", Map.of("originCityId", delhi,
                "destinationCityId", lucknow, "departsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "travelMode", "BUS", "capacityGrams", 5000, "pricePerKgPaise", 10000)).getBody().get("id")).longValue();
        long parcelId = ((Number) call(sender.token, HttpMethod.POST, "/parcels", Map.of("originCityId", delhi,
                "destinationCityId", lucknow, "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "windowEnd", Instant.now().plus(3, ChronoUnit.DAYS).toString(), "categoryId", docs, "weightGrams", 1000,
                "description", "Papers", "declaredValuePaise", 10000, "recipientName", "Mom")).getBody().get("id")).longValue();
        long bookingId = ((Number) call(sender.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", parcelId))
                .getBody().get("id")).longValue();

        List<Object> seen = new ArrayList<>();
        Map<String, Object> view = null;
        for (int i = 0; i < 10; i++) {
            var r = call(sender.token, HttpMethod.POST, "/demo/bookings/" + bookingId + "/advance", null);
            if (r.getStatusCode() != HttpStatus.OK) {
                break;
            }
            view = r.getBody();
            seen.add(view.get("status") + "/" + view.get("paymentStatus"));
        }
        assertEquals("DELIVERED", view.get("status"), seen.toString());
        assertEquals("RELEASED", view.get("paymentStatus"), seen.toString());
        assertTrue(seen.contains("HANDED_OVER/HELD") && seen.contains("IN_TRANSIT/HELD"), seen.toString());

        // The simulated steps went through the real engine, so the chain of custody is complete and still valid.
        assertEquals(true, call(sender.token, HttpMethod.GET, "/bookings/" + bookingId + "/events/verify", null).getBody().get("valid"));

        // Strangers cannot drive someone else's booking.
        Acct stranger = register("Stranger");
        assertEquals(HttpStatus.FORBIDDEN, call(stranger.token, HttpMethod.POST, "/demo/bookings/" + bookingId + "/advance", null).getStatusCode());
        assertEquals(true, call(null, HttpMethod.GET, "/public/config", null).getBody().get("demoMode"));
    }
}
