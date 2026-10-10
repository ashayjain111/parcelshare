package com.parcelshare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end tests over real HTTP against an in-memory database. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${TEST_DB_USER:sa}",
                "spring.datasource.password=${TEST_DB_PASSWORD:}",
                "parcelshare.release-window-hours=0",
                "parcelshare.rate-limit-per-minute=100000",
                "parcelshare.allow-dev-defaults=true",
                "parcelshare.admin-password=TestAdmin123!"
        })
class FullFlowTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    // ---------------------------------------------------------------- helpers

    record Acct(long id, String token) {
    }

    ResponseEntity<Map<String, Object>> call(String token, HttpMethod m, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        return http.exchange(path, m, new HttpEntity<>(body, h), MAP);
    }

    ResponseEntity<List<Map<String, Object>>> list(String token, String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), LIST);
    }

    Acct register(String name) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 6) + "@test.local";
        var r = call(null, HttpMethod.POST, "/auth/register",
                Map.of("email", email, "password", "password123", "fullName", name, "phone", "9999900000"));
        assertEquals(HttpStatus.CREATED, r.getStatusCode(), String.valueOf(r.getBody()));
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) r.getBody().get("user");
        return new Acct(((Number) user.get("id")).longValue(), (String) r.getBody().get("token"));
    }

    String adminToken() {
        var r = call(null, HttpMethod.POST, "/auth/login", Map.of("email", "admin@parcelshare.local", "password", "TestAdmin123!"));
        assertEquals(HttpStatus.OK, r.getStatusCode(), String.valueOf(r.getBody()));
        return (String) r.getBody().get("token");
    }

    Acct verifiedTraveller(String name) {
        Acct t = register(name);
        assertEquals(HttpStatus.OK, call(t.token, HttpMethod.POST, "/me/verification", Map.of("note", "ID checked")).getStatusCode());
        assertEquals(HttpStatus.OK, call(adminToken(), HttpMethod.POST, "/admin/verifications/" + t.id + "/decision",
                Map.of("approve", true)).getStatusCode());
        return t;
    }

    long cityId(String name) {
        return list(adminToken(), "/cities").getBody().stream().filter(c -> name.equals(c.get("name")))
                .map(c -> ((Number) c.get("id")).longValue()).findFirst().orElseThrow();
    }

    long categoryId(String name) {
        return list(adminToken(), "/parcels/categories").getBody().stream().filter(c -> name.equals(c.get("name")))
                .map(c -> ((Number) c.get("id")).longValue()).findFirst().orElseThrow();
    }

    long postTrip(Acct traveller, int capacityGrams) {
        var r = call(traveller.token, HttpMethod.POST, "/trips", Map.of(
                "originCityId", cityId("Delhi"), "destinationCityId", cityId("Lucknow"),
                "departsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "travelMode", "BUS",
                "capacityGrams", capacityGrams, "pricePerKgPaise", 10000));
        assertEquals(HttpStatus.CREATED, r.getStatusCode(), String.valueOf(r.getBody()));
        return ((Number) r.getBody().get("id")).longValue();
    }

    long postParcel(Acct sender, int grams, String category, String description) {
        var r = call(sender.token, HttpMethod.POST, "/parcels", Map.of(
                "originCityId", cityId("Noida"), "destinationCityId", cityId("Lucknow"),
                "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "windowEnd", Instant.now().plus(3, ChronoUnit.DAYS).toString(),
                "categoryId", categoryId(category), "weightGrams", grams, "description", description,
                "declaredValuePaise", 50000, "recipientName", "Mom", "recipientPhone", "9888800000"));
        assertEquals(HttpStatus.CREATED, r.getStatusCode(), String.valueOf(r.getBody()));
        return ((Number) r.getBody().get("id")).longValue();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void happyPathFromRequestToReleasedPayment() {
        Acct traveller = verifiedTraveller("Rahul");
        Acct sender = register("Ananya");
        long tripId = postTrip(traveller, 5000);
        long parcelId = postParcel(sender, 1000, "Documents", "Hostel admission papers");

        // Matching: Noida parcel should find the Delhi trip (same region), ranked with a score breakdown.
        var matches = list(sender.token, "/parcels/" + parcelId + "/matches").getBody();
        assertTrue(matches.stream().anyMatch(m -> ((Map<?, ?>) m.get("trip")).get("id").equals((int) tripId)
                || ((Number) ((Map<?, ?>) m.get("trip")).get("id")).longValue() == tripId));

        var booked = call(sender.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", parcelId));
        assertEquals(HttpStatus.CREATED, booked.getStatusCode(), String.valueOf(booked.getBody()));
        long bookingId = ((Number) booked.getBody().get("id")).longValue();
        assertEquals("REQUESTED", booked.getBody().get("status"));
        assertEquals(10000, ((Number) booked.getBody().get("pricePaise")).intValue()); // 1 kg * Rs100
        assertEquals(9000, ((Number) booked.getBody().get("travellerPayoutPaise")).intValue()); // 10% commission

        // Strangers cannot touch the booking.
        Acct stranger = register("Mallory");
        assertEquals(HttpStatus.FORBIDDEN, call(stranger.token, HttpMethod.GET, "/bookings/" + bookingId, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, call(stranger.token, HttpMethod.POST, "/bookings/" + bookingId + "/accept", null).getStatusCode());
        // Only the traveller may accept.
        assertEquals(HttpStatus.FORBIDDEN, call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/accept", null).getStatusCode());

        assertEquals("ACCEPTED", call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/accept", null).getBody().get("status"));

        // Handover: wrong code is rejected, right code works, and the traveller must attest to inspecting contents.
        var code = (String) call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/code", null).getBody().get("code");
        assertNotNull(code);
        assertEquals(HttpStatus.BAD_REQUEST, call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/confirm",
                Map.of("code", code, "contentsChecked", false)).getStatusCode());
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertEquals(HttpStatus.BAD_REQUEST, call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/confirm",
                Map.of("code", wrong, "contentsChecked", true)).getStatusCode());
        var handed = call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/confirm",
                Map.of("code", code, "contentsChecked", true));
        assertEquals("HANDED_OVER", handed.getBody().get("status"), String.valueOf(handed.getBody()));
        // The code is single-use.
        assertEquals(HttpStatus.CONFLICT, call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/confirm",
                Map.of("code", code, "contentsChecked", true)).getStatusCode());

        assertEquals("IN_TRANSIT", call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/start", null).getBody().get("status"));
        assertEquals(HttpStatus.OK, call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/arrive", null).getStatusCode());

        // Delivery: the sender (and recipient link) know the code; a wrong code fails.
        String deliveryCode = (String) call(sender.token, HttpMethod.GET, "/bookings/" + bookingId, null).getBody().get("deliveryCode");
        assertNotNull(deliveryCode);
        assertEquals(HttpStatus.BAD_REQUEST, call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/deliver",
                Map.of("code", deliveryCode.equals("123456") ? "654321" : "123456")).getStatusCode());
        // The traveller's own view must NOT leak the delivery code.
        assertEquals(null, call(traveller.token, HttpMethod.GET, "/bookings/" + bookingId, null).getBody().get("deliveryCode"));

        var delivered = call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/deliver", Map.of("code", deliveryCode));
        assertEquals("DELIVERED", delivered.getBody().get("status"), String.valueOf(delivered.getBody()));
        assertEquals("RELEASED", delivered.getBody().get("paymentStatus"));

        // Ratings: once per side.
        assertEquals(HttpStatus.CREATED, call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/rating",
                Map.of("score", 5, "comment", "Great")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/rating",
                Map.of("score", 5)).getStatusCode());

        // Chain of custody: complete, ordered, and verifiable.
        var events = list(sender.token, "/bookings/" + bookingId + "/events").getBody();
        List<Object> types = new ArrayList<>(events.stream().map(e -> e.get("type")).toList());
        assertTrue(types.containsAll(List.of("BOOKING_REQUESTED", "BOOKING_ACCEPTED", "HANDOVER_CONFIRMED",
                "JOURNEY_STARTED", "ARRIVED", "DELIVERY_CONFIRMED", "PAYMENT_RELEASED")), types.toString());
        assertEquals(true, call(sender.token, HttpMethod.GET, "/bookings/" + bookingId + "/events/verify", null).getBody().get("valid"));

        // Tamper with history directly in the DB: verification must now fail.
        jdbc.update("UPDATE tracking_event SET payload = 'tampered' WHERE booking_id = ? AND type = 'BOOKING_ACCEPTED'", bookingId);
        var verdict = call(sender.token, HttpMethod.GET, "/bookings/" + bookingId + "/events/verify", null).getBody();
        assertEquals(false, verdict.get("valid"));
        assertNotNull(verdict.get("firstBrokenEventId"));
    }

    @Test
    void capacityCannotBeOverbooked() {
        Acct traveller = verifiedTraveller("Meera");
        long tripId = postTrip(traveller, 5000);

        Acct s1 = register("S1");
        long p1 = postParcel(s1, 3000, "Clothes", "Winter clothes");
        assertEquals(HttpStatus.CREATED, call(s1.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", p1)).getStatusCode());

        Acct s2 = register("S2");
        long p2 = postParcel(s2, 3000, "Clothes", "More clothes");
        var second = call(s2.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", p2));
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode(), String.valueOf(second.getBody()));
        // The failed attempt must not have leaked a reservation or locked the parcel.
        assertEquals("OPEN", call(s2.token, HttpMethod.GET, "/parcels/" + p2, null).getBody().get("status"));
        assertEquals(3000, ((Number) call(s1.token, HttpMethod.GET, "/trips/" + tripId, null).getBody().get("reservedGrams")).intValue());
    }

    @Test
    void concurrentBookingsNeverExceedCapacity() throws Exception {
        Acct traveller = verifiedTraveller("Karan");
        long tripId = postTrip(traveller, 5000);
        int racers = 6; // each wants 2 kg, only 2 can fit
        List<Acct> senders = new ArrayList<>();
        List<Long> parcels = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            Acct s = register("Racer" + i);
            senders.add(s);
            parcels.add(postParcel(s, 2000, "Books & stationery", "Textbooks " + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<Future<HttpStatusCode>> futures = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            final int n = i;
            Callable<HttpStatusCode> task = () -> new HttpStatusCode(call(senders.get(n).token, HttpMethod.POST, "/bookings",
                    Map.of("tripId", tripId, "parcelId", parcels.get(n))).getStatusCode().value());
            futures.add(pool.submit(task));
        }
        int ok = 0;
        for (var f : futures) {
            if (f.get().code == 201) {
                ok++;
            }
        }
        pool.shutdown();
        assertEquals(2, ok, "exactly floor(5000/2000)=2 bookings may succeed");
        assertEquals(4000, ((Number) call(traveller.token, HttpMethod.GET, "/trips/" + tripId, null).getBody().get("reservedGrams")).intValue());
    }

    record HttpStatusCode(int code) {
    }

    @Test
    void prohibitedAndSuspiciousParcelsAreRefused() {
        Acct sender = register("Sam");
        var prohibited = call(sender.token, HttpMethod.POST, "/parcels", Map.of(
                "originCityId", cityId("Delhi"), "destinationCityId", cityId("Lucknow"),
                "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "windowEnd", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "categoryId", categoryId("Medicines"), "weightGrams", 200, "description", "tablets",
                "declaredValuePaise", 1000, "recipientName", "X"));
        assertEquals(HttpStatus.BAD_REQUEST, prohibited.getStatusCode());

        var keyword = call(sender.token, HttpMethod.POST, "/parcels", Map.of(
                "originCityId", cityId("Delhi"), "destinationCityId", cityId("Lucknow"),
                "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "windowEnd", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "categoryId", categoryId("Documents"), "weightGrams", 200, "description", "some cash inside",
                "declaredValuePaise", 1000, "recipientName", "X"));
        assertEquals(HttpStatus.BAD_REQUEST, keyword.getStatusCode());

        // Restricted category is accepted but held for admin review and cannot be booked yet.
        long id = postParcel(sender, 500, "Small electronics", "USB hub");
        assertEquals("PENDING_REVIEW", call(sender.token, HttpMethod.GET, "/parcels/" + id, null).getBody().get("status"));
    }

    /** Regression: the browser app lives on another origin, so preflight requests must be allowed (and only for it). */
    @Test
    void corsPreflightAllowsTheFrontendOriginOnly() {
        HttpHeaders good = new HttpHeaders();
        good.set("Origin", "http://localhost:5180");
        good.set("Access-Control-Request-Method", "POST");
        good.set("Access-Control-Request-Headers", "content-type,authorization");
        var ok = http.exchange("/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(good), String.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals("http://localhost:5180", ok.getHeaders().getFirst("Access-Control-Allow-Origin"));

        HttpHeaders evil = new HttpHeaders();
        evil.set("Origin", "http://evil.example");
        evil.set("Access-Control-Request-Method", "POST");
        var denied = http.exchange("/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(evil), String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    }

    @Test
    void demoHelpersDoNotExistUnlessDemoModeIsOn() {
        Acct a = register("Curious");
        assertEquals(HttpStatus.NOT_FOUND, call(a.token, HttpMethod.POST, "/demo/bookings/1/advance", null).getStatusCode());
        assertEquals(false, call(null, HttpMethod.GET, "/public/config", null).getBody().get("demoMode"));
    }

    @Test
    void unverifiedTravellerCannotPostTrips() {
        Acct t = register("Newbie");
        var r = call(t.token, HttpMethod.POST, "/trips", Map.of(
                "originCityId", cityId("Delhi"), "destinationCityId", cityId("Lucknow"),
                "departsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "travelMode", "BUS",
                "capacityGrams", 2000, "pricePerKgPaise", 10000));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
    }

    @Test
    void travellerCancellationRefundsAndReopensTheParcel() {
        Acct traveller = verifiedTraveller("Flaky");
        Acct sender = register("Patient");
        long tripId = postTrip(traveller, 5000);
        long parcelId = postParcel(sender, 1000, "Documents", "Certificates");
        long bookingId = ((Number) call(sender.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", parcelId))
                .getBody().get("id")).longValue();
        call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/accept", null);

        var cancelled = call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/cancel", null);
        assertEquals("CANCELLED", cancelled.getBody().get("status"));
        assertEquals("REFUNDED", cancelled.getBody().get("paymentStatus"));
        assertEquals("OPEN", call(sender.token, HttpMethod.GET, "/parcels/" + parcelId, null).getBody().get("status"));
        assertEquals(0, ((Number) call(sender.token, HttpMethod.GET, "/trips/" + tripId, null).getBody().get("reservedGrams")).intValue());
        // The traveller's reliability took a hit.
        assertFalse(((Number) call(traveller.token, HttpMethod.GET, "/me", null).getBody().get("reliabilityPercent")).intValue() >= 90);
    }

    @Test
    void disputeFreezesMoneyAndAdminCanSplitIt() {
        Acct traveller = verifiedTraveller("Dev");
        Acct sender = register("Dina");
        long tripId = postTrip(traveller, 5000);
        long parcelId = postParcel(sender, 1000, "Clothes", "Jacket");
        long bookingId = ((Number) call(sender.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", parcelId))
                .getBody().get("id")).longValue();
        call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/accept", null);
        String code = (String) call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/code", null).getBody().get("code");
        call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/confirm", Map.of("code", code, "contentsChecked", true));
        call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/start", null);

        var disp = call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/dispute", Map.of("reason", "Parcel never arrived"));
        assertEquals(HttpStatus.OK, disp.getStatusCode(), String.valueOf(disp.getBody()));
        long disputeId = ((Number) disp.getBody().get("id")).longValue();
        assertEquals("ON_DISPUTE_HOLD", call(sender.token, HttpMethod.GET, "/bookings/" + bookingId, null).getBody().get("paymentStatus"));

        // Normal users cannot resolve disputes.
        assertEquals(HttpStatus.FORBIDDEN, call(sender.token, HttpMethod.POST, "/admin/disputes/" + disputeId + "/resolve",
                Map.of("resolution", "REFUND")).getStatusCode());

        var resolved = call(adminToken(), HttpMethod.POST, "/admin/disputes/" + disputeId + "/resolve",
                Map.of("resolution", "PARTIAL", "travellerSharePaise", 4000, "note", "Split 40/60"));
        assertEquals(HttpStatus.OK, resolved.getStatusCode(), String.valueOf(resolved.getBody()));
        var b = call(sender.token, HttpMethod.GET, "/bookings/" + bookingId, null).getBody();
        assertEquals("RESOLVED", b.get("status"));
        assertEquals("PARTIALLY_RELEASED", b.get("paymentStatus"));
        // Ledger must balance: money out of escrow equals money in.
        Long escrowIn = jdbc.queryForObject("SELECT COALESCE(SUM(amount_paise),0) FROM ledger_entry WHERE account='ESCROW' AND direction='CREDIT' "
                + "AND payment_id=(SELECT id FROM payment WHERE booking_id=?)", Long.class, bookingId);
        Long escrowOut = jdbc.queryForObject("SELECT COALESCE(SUM(amount_paise),0) FROM ledger_entry WHERE account='ESCROW' AND direction='DEBIT' "
                + "AND payment_id=(SELECT id FROM payment WHERE booking_id=?)", Long.class, bookingId);
        assertEquals(escrowIn, escrowOut);
    }
}
