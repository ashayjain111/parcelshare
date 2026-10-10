package com.parcelshare;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parcelshare.common.AppProps;
import com.parcelshare.notification.MailService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;

/** Traveller offers, in-app notifications, trip cancellation, password reset/change, profile and reviews. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:gaps;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${TEST_DB_USER:sa}",
                "spring.datasource.password=${TEST_DB_PASSWORD:}",
                "parcelshare.rate-limit-per-minute=100000",
                "parcelshare.release-window-hours=48",
                "parcelshare.allow-dev-defaults=true",
                "parcelshare.admin-password=TestAdmin123!"
        })
class GapsTest {

    static class CapturingMail extends MailService {
        record Sent(String to, String subject, String body) {
        }

        private final List<Sent> sentMail = new CopyOnWriteArrayList<>();

        CapturingMail(ObjectProvider<JavaMailSender> s, AppProps p) {
            super(s, p);
        }

        List<Sent> sent() {
            return sentMail;
        }

        @Override
        public void send(String to, String subject, String body) {
            sentMail.add(new Sent(to, subject, body));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        CapturingMail capturingMail(ObjectProvider<JavaMailSender> s, AppProps p) {
            return new CapturingMail(s, p);
        }
    }

    @Autowired
    TestRestTemplate http;
    @Autowired
    CapturingMail mail;

    static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    // ------------------------------------------------------------ helpers

    record Acct(long id, String token, String email) {
    }

    ResponseEntity<Map<String, Object>> call(String token, HttpMethod m, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        return http.exchange(path, m, new HttpEntity<>(body, h), MAP);
    }

    List<Map<String, Object>> list(String token, String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), LIST).getBody();
    }

    Acct register(String name) {
        String email = name.toLowerCase() + UUID.randomUUID().toString().substring(0, 6) + "@t.local";
        var r = call(null, HttpMethod.POST, "/auth/register", Map.of("email", email, "password", "password123", "fullName", name));
        @SuppressWarnings("unchecked")
        var u = (Map<String, Object>) r.getBody().get("user");
        return new Acct(((Number) u.get("id")).longValue(), (String) r.getBody().get("token"), email);
    }

    String admin() {
        return (String) call(null, HttpMethod.POST, "/auth/login",
                Map.of("email", "admin@parcelshare.local", "password", "TestAdmin123!")).getBody().get("token");
    }

    Acct traveller(String name) {
        Acct t = register(name);
        call(t.token, HttpMethod.POST, "/me/verification", Map.of("note", "ok"));
        call(admin(), HttpMethod.POST, "/admin/verifications/" + t.id + "/decision", Map.of("approve", true));
        return t;
    }

    long idOf(String path, String name) {
        return list(admin(), path).stream().filter(c -> name.equals(c.get("name"))).map(c -> ((Number) c.get("id")).longValue())
                .findFirst().orElseThrow();
    }

    long trip(Acct t, int capacity) {
        return ((Number) call(t.token, HttpMethod.POST, "/trips", Map.of("originCityId", idOf("/cities", "Delhi"),
                "destinationCityId", idOf("/cities", "Lucknow"), "departsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "travelMode", "BUS", "capacityGrams", capacity, "pricePerKgPaise", 10000)).getBody().get("id")).longValue();
    }

    long parcel(Acct s, int grams) {
        return ((Number) call(s.token, HttpMethod.POST, "/parcels", Map.of("originCityId", idOf("/cities", "Delhi"),
                "destinationCityId", idOf("/cities", "Lucknow"), "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "windowEnd", Instant.now().plus(3, ChronoUnit.DAYS).toString(), "categoryId", idOf("/parcels/categories", "Documents"),
                "weightGrams", grams, "description", "Papers", "declaredValuePaise", 10000, "recipientName", "Mom")).getBody().get("id")).longValue();
    }

    long book(Acct sender, long tripId, long parcelId) {
        var r = call(sender.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", parcelId));
        assertEquals(HttpStatus.CREATED, r.getStatusCode(), String.valueOf(r.getBody()));
        return ((Number) r.getBody().get("id")).longValue();
    }

    // ------------------------------------------------------------ traveller offers

    @Test
    void travellerCanOfferToCarryAndTheSenderIsToldAndSeesIt() {
        Acct trav = traveller("Offerer");
        Acct sender = register("Wanter");
        long tripId = trip(trav, 5000);
        long parcelId = parcel(sender, 1000);

        // The traveller sees the parcel among their matches, not yet offered.
        var matches = list(trav.token, "/trips/" + tripId + "/matches");
        var mine = matches.stream().filter(m -> ((Number) ((Map<?, ?>) m.get("parcel")).get("id")).longValue() == parcelId).findFirst().orElseThrow();
        assertEquals(false, mine.get("offered"));

        assertEquals(HttpStatus.CREATED, call(trav.token, HttpMethod.POST, "/trips/" + tripId + "/offers", Map.of("parcelId", parcelId)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, call(trav.token, HttpMethod.POST, "/trips/" + tripId + "/offers", Map.of("parcelId", parcelId)).getStatusCode());

        // Strangers and the sender cannot make offers on someone else's trip.
        assertEquals(HttpStatus.FORBIDDEN, call(sender.token, HttpMethod.POST, "/trips/" + tripId + "/offers", Map.of("parcelId", parcelId)).getStatusCode());

        // The sender gets an in-app notification + an email, and the match is flagged as offered.
        var inbox = call(sender.token, HttpMethod.GET, "/notifications", null).getBody();
        assertTrue(((Number) inbox.get("unread")).intValue() >= 1);
        assertTrue(String.valueOf(inbox.get("items")).contains("OFFER_RECEIVED"));
        await().atMost(Duration.ofSeconds(5)).until(() -> mail.sent().stream().anyMatch(m -> m.to().equals(sender.email) && m.subject().contains("can carry")));
        var tripMatches = list(sender.token, "/parcels/" + parcelId + "/matches");
        assertEquals(true, tripMatches.stream().filter(m -> ((Number) ((Map<?, ?>) m.get("trip")).get("id")).longValue() == tripId).findFirst().orElseThrow().get("offered"));
        assertEquals(true, list(trav.token, "/trips/" + tripId + "/matches").stream()
                .filter(m -> ((Number) ((Map<?, ?>) m.get("parcel")).get("id")).longValue() == parcelId).findFirst().orElseThrow().get("offered"));

        // A parcel that does not fit (1 kg parcel, trip with only 500 g spare) cannot be offered for.
        long tinyTrip = trip(trav, 500);
        assertEquals(HttpStatus.BAD_REQUEST, call(trav.token, HttpMethod.POST, "/trips/" + tinyTrip + "/offers", Map.of("parcelId", parcelId)).getStatusCode());
    }

    // ------------------------------------------------------------ in-app notifications

    @Test
    void bookingMilestonesFillTheBellAndCanBeMarkedRead() {
        Acct trav = traveller("Bell");
        Acct sender = register("Ringer");
        long bookingId = book(sender, trip(trav, 5000), parcel(sender, 1000));
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertTrue(((Number) call(trav.token, HttpMethod.GET, "/notifications", null).getBody().get("unread")).intValue() >= 1));
        var items = (List<?>) call(trav.token, HttpMethod.GET, "/notifications", null).getBody().get("items");
        @SuppressWarnings("unchecked")
        var first = (Map<String, Object>) items.get(0);
        assertEquals("/bookings/" + bookingId, first.get("link"));

        // Privacy: the sender's own bell has nothing about the traveller's inbox.
        assertFalse(String.valueOf(call(sender.token, HttpMethod.GET, "/notifications", null).getBody().get("items")).contains("New parcel request"));

        call(trav.token, HttpMethod.POST, "/notifications/read-all", null);
        assertEquals(0, ((Number) call(trav.token, HttpMethod.GET, "/notifications", null).getBody().get("unread")).intValue());
        assertEquals(HttpStatus.UNAUTHORIZED, call(null, HttpMethod.GET, "/notifications", null).getStatusCode());
    }

    // ------------------------------------------------------------ trip cancellation

    @Test
    void cancellingATripRefundsEveryoneAndIsBlockedOnceAParcelIsInHand() {
        Acct trav = traveller("Canceller");
        long tripId = trip(trav, 9000);
        Acct s1 = register("S1");
        Acct s2 = register("S2");
        long b1 = book(s1, tripId, parcel(s1, 1000));   // stays REQUESTED
        long b2 = book(s2, tripId, parcel(s2, 1000));   // becomes ACCEPTED
        call(trav.token, HttpMethod.POST, "/bookings/" + b2 + "/accept", null);

        assertEquals(HttpStatus.FORBIDDEN, call(s1.token, HttpMethod.POST, "/trips/" + tripId + "/cancel", null).getStatusCode());
        var cancelled = call(trav.token, HttpMethod.POST, "/trips/" + tripId + "/cancel", null);
        assertEquals("CANCELLED", cancelled.getBody().get("status"), String.valueOf(cancelled.getBody()));
        assertEquals("REFUNDED", call(s1.token, HttpMethod.GET, "/bookings/" + b1, null).getBody().get("paymentStatus"));
        assertEquals("REFUNDED", call(s2.token, HttpMethod.GET, "/bookings/" + b2, null).getBody().get("paymentStatus"));
        assertEquals("REJECTED", call(s1.token, HttpMethod.GET, "/bookings/" + b1, null).getBody().get("status"));
        assertEquals("CANCELLED", call(s2.token, HttpMethod.GET, "/bookings/" + b2, null).getBody().get("status"));
        assertEquals(HttpStatus.CONFLICT, call(trav.token, HttpMethod.POST, "/trips/" + tripId + "/cancel", null).getStatusCode());

        // Once a parcel has been handed over, the trip cannot be cancelled.
        Acct trav2 = traveller("Committed");
        long trip2 = trip(trav2, 5000);
        Acct s3 = register("S3");
        long b3 = book(s3, trip2, parcel(s3, 1000));
        call(trav2.token, HttpMethod.POST, "/bookings/" + b3 + "/accept", null);
        String code = (String) call(s3.token, HttpMethod.POST, "/bookings/" + b3 + "/handover/code", null).getBody().get("code");
        call(trav2.token, HttpMethod.POST, "/bookings/" + b3 + "/handover/confirm", Map.of("code", code, "contentsChecked", true));
        assertEquals(HttpStatus.CONFLICT, call(trav2.token, HttpMethod.POST, "/trips/" + trip2 + "/cancel", null).getStatusCode());
    }

    // ------------------------------------------------------------ password reset / change / profile / reviews

    @Test
    void forgotAndResetPasswordWorksOnceAndNeverRevealsWhoHasAnAccount() {
        Acct a = register("Forgetful");
        var unknown = call(null, HttpMethod.POST, "/auth/forgot", Map.of("email", "nobody-here@t.local"));
        var known = call(null, HttpMethod.POST, "/auth/forgot", Map.of("email", a.email));
        assertEquals(HttpStatus.OK, unknown.getStatusCode());
        assertEquals(unknown.getBody(), known.getBody(), "identical answer for known and unknown addresses");

        await().atMost(Duration.ofSeconds(5)).until(() -> mail.sent().stream().anyMatch(m -> m.to().equals(a.email) && m.subject().startsWith("Reset")));
        var email = mail.sent().stream().filter(m -> m.to().equals(a.email) && m.subject().startsWith("Reset")).findFirst().orElseThrow();
        assertFalse(mail.sent().stream().anyMatch(m -> m.to().equals("nobody-here@t.local")));
        String token = email.body().substring(email.body().indexOf("token=") + 6).split("\\s")[0];

        assertEquals(HttpStatus.BAD_REQUEST, call(null, HttpMethod.POST, "/auth/reset", Map.of("token", token, "password", "short")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, call(null, HttpMethod.POST, "/auth/reset", Map.of("token", "deadbeef", "password", "brandnewpass1")).getStatusCode());
        assertEquals(HttpStatus.OK, call(null, HttpMethod.POST, "/auth/reset", Map.of("token", token, "password", "brandnewpass1")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, call(null, HttpMethod.POST, "/auth/reset", Map.of("token", token, "password", "anotherpass22")).getStatusCode(), "single use");

        assertEquals(HttpStatus.OK, call(null, HttpMethod.POST, "/auth/login", Map.of("email", a.email, "password", "brandnewpass1")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(null, HttpMethod.POST, "/auth/login", Map.of("email", a.email, "password", "password123")).getStatusCode());
    }

    @Test
    void profileCanBeEditedPasswordChangedAndReviewsRead() {
        Acct trav = traveller("Reviewed");
        Acct sender = register("Reviewer");
        long bookingId = book(sender, trip(trav, 5000), parcel(sender, 1000));
        for (int i = 0; i < 6; i++) { // walk the booking to DELIVERED through the real endpoints
            if (i == 0) {
                call(trav.token, HttpMethod.POST, "/bookings/" + bookingId + "/accept", null);
            }
        }
        String code = (String) call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/code", null).getBody().get("code");
        call(trav.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/confirm", Map.of("code", code, "contentsChecked", true));
        call(trav.token, HttpMethod.POST, "/bookings/" + bookingId + "/start", null);
        String delivery = (String) call(sender.token, HttpMethod.GET, "/bookings/" + bookingId, null).getBody().get("deliveryCode");
        call(trav.token, HttpMethod.POST, "/bookings/" + bookingId + "/deliver", Map.of("code", delivery));
        assertEquals(HttpStatus.CREATED, call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/rating", Map.of("score", 5, "comment", "Careful and punctual")).getStatusCode());

        var reviews = list(trav.token, "/me/reviews");
        assertEquals(1, reviews.size());
        assertEquals("Careful and punctual", reviews.get(0).get("comment"));
        assertEquals("Reviewer", reviews.get(0).get("fromName"));
        assertEquals(0, list(sender.token, "/me/reviews").size());

        var patched = call(trav.token, HttpMethod.PATCH, "/me", Map.of("fullName", "Reviewed Renamed", "phone", "9876500000"));
        assertEquals("Reviewed Renamed", patched.getBody().get("fullName"));
        assertEquals("9876500000", patched.getBody().get("phone"));
        assertEquals(HttpStatus.BAD_REQUEST, call(trav.token, HttpMethod.PATCH, "/me", Map.of("fullName", "", "phone", "1")).getStatusCode());

        assertEquals(HttpStatus.BAD_REQUEST, call(trav.token, HttpMethod.POST, "/me/password", Map.of("current", "wrong-password", "next", "freshpassword9")).getStatusCode());
        assertEquals(HttpStatus.OK, call(trav.token, HttpMethod.POST, "/me/password", Map.of("current", "password123", "next", "freshpassword9")).getStatusCode());
        assertEquals(HttpStatus.OK, call(null, HttpMethod.POST, "/auth/login", Map.of("email", trav.email, "password", "freshpassword9")).getStatusCode());
    }
}
