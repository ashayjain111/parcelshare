package com.parcelshare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parcelshare.jobs.ScheduledJobs;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Session invalidation, data export, account deletion and photo retention. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:privacy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${TEST_DB_USER:sa}",
                "spring.datasource.password=${TEST_DB_PASSWORD:}",
                "parcelshare.rate-limit-per-minute=100000",
                "parcelshare.release-window-hours=48",
                "parcelshare.allow-dev-defaults=true",
                "parcelshare.admin-password=TestAdmin123!"
        })
class PrivacyTest {

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ScheduledJobs jobs;

    static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    record Acct(long id, String token, String email) {
    }

    ResponseEntity<Map<String, Object>> call(String token, HttpMethod m, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        return http.exchange(path, m, new HttpEntity<>(body, h), MAP);
    }

    Acct register(String name) {
        String email = name.toLowerCase() + UUID.randomUUID().toString().substring(0, 6) + "@t.local";
        var r = call(null, HttpMethod.POST, "/auth/register", Map.of("email", email, "password", "password123", "fullName", name, "phone", "9876543210"));
        @SuppressWarnings("unchecked")
        var u = (Map<String, Object>) r.getBody().get("user");
        return new Acct(((Number) u.get("id")).longValue(), (String) r.getBody().get("token"), email);
    }

    String login(String email, String password) {
        var r = call(null, HttpMethod.POST, "/auth/login", Map.of("email", email, "password", password));
        return (String) r.getBody().get("token");
    }

    String admin() {
        return login("admin@parcelshare.local", "TestAdmin123!");
    }

    long idOf(String path, String name) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(admin());
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), LIST).getBody().stream()
                .filter(c -> name.equals(c.get("name"))).map(c -> ((Number) c.get("id")).longValue()).findFirst().orElseThrow();
    }

    Acct traveller(String name) {
        Acct t = register(name);
        call(t.token, HttpMethod.POST, "/me/verification", Map.of("note", "ok"));
        call(admin(), HttpMethod.POST, "/admin/verifications/" + t.id + "/decision", Map.of("approve", true));
        return t;
    }

    long trip(Acct t) {
        return ((Number) call(t.token, HttpMethod.POST, "/trips", Map.of("originCityId", idOf("/cities", "Delhi"),
                "destinationCityId", idOf("/cities", "Lucknow"), "departsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "travelMode", "BUS", "capacityGrams", 5000, "pricePerKgPaise", 10000)).getBody().get("id")).longValue();
    }

    long parcel(Acct s) {
        return ((Number) call(s.token, HttpMethod.POST, "/parcels", Map.of("originCityId", idOf("/cities", "Delhi"),
                "destinationCityId", idOf("/cities", "Lucknow"), "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "windowEnd", Instant.now().plus(3, ChronoUnit.DAYS).toString(), "categoryId", idOf("/parcels/categories", "Documents"),
                "weightGrams", 800, "description", "Papers", "declaredValuePaise", 10000, "recipientName", "Mom",
                "recipientPhone", "9000000000")).getBody().get("id")).longValue();
    }

    static byte[] jpeg() throws Exception {
        BufferedImage img = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    void addPhoto(String token, long bookingId) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("kind", "CONTENTS");
        body.add("file", new ByteArrayResource(jpeg()) {
            @Override
            public String getFilename() {
                return "p.jpg";
            }
        });
        assertEquals(HttpStatus.CREATED, http.exchange("/bookings/" + bookingId + "/evidence", HttpMethod.POST, new HttpEntity<>(body, h), MAP).getStatusCode());
    }

    /** A booking at ACCEPTED, with one photo attached. */
    long acceptedBookingWithPhoto(Acct trav, Acct sender) throws Exception {
        long id = ((Number) call(sender.token, HttpMethod.POST, "/bookings", Map.of("tripId", trip(trav), "parcelId", parcel(sender)))
                .getBody().get("id")).longValue();
        call(trav.token, HttpMethod.POST, "/bookings/" + id + "/accept", null);
        addPhoto(sender.token, id);
        return id;
    }

    void deliver(Acct trav, Acct sender, long bookingId) {
        String code = (String) call(sender.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/code", null).getBody().get("code");
        call(trav.token, HttpMethod.POST, "/bookings/" + bookingId + "/handover/confirm", Map.of("code", code, "contentsChecked", true));
        call(trav.token, HttpMethod.POST, "/bookings/" + bookingId + "/start", null);
        String delivery = (String) call(sender.token, HttpMethod.GET, "/bookings/" + bookingId, null).getBody().get("deliveryCode");
        assertEquals("DELIVERED", call(trav.token, HttpMethod.POST, "/bookings/" + bookingId + "/deliver", Map.of("code", delivery)).getBody().get("status"));
    }

    long photoCount(long bookingId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM evidence WHERE booking_id = ?", Long.class, bookingId);
    }

    // ---------------------------------------------------------------- sessions

    @Test
    void changingPasswordSignsOutEveryOtherSessionButKeepsThisOne() {
        Acct a = register("Sessions");
        String phone = login(a.email, "password123");   // a second device
        assertEquals(HttpStatus.OK, call(a.token, HttpMethod.GET, "/me", null).getStatusCode());
        assertEquals(HttpStatus.OK, call(phone, HttpMethod.GET, "/me", null).getStatusCode());

        var changed = call(a.token, HttpMethod.POST, "/me/password", Map.of("current", "password123", "next", "a-new-password-9"));
        assertEquals(HttpStatus.OK, changed.getStatusCode());
        String fresh = (String) changed.getBody().get("token");

        assertEquals(HttpStatus.UNAUTHORIZED, call(a.token, HttpMethod.GET, "/me", null).getStatusCode(), "the old token is dead");
        assertEquals(HttpStatus.UNAUTHORIZED, call(phone, HttpMethod.GET, "/me", null).getStatusCode(), "the other device is signed out");
        assertEquals(HttpStatus.OK, call(fresh, HttpMethod.GET, "/me", null).getStatusCode(), "the device that changed it stays signed in");
    }

    // ---------------------------------------------------------------- export

    @Test
    void exportContainsMyDataAndNobodyElses() {
        Acct me = register("Exporter");
        Acct other = register("Bystander");
        long mine = parcel(me);
        long theirs = parcel(other);

        var export = call(me.token, HttpMethod.GET, "/me/export", null);
        assertEquals(HttpStatus.OK, export.getStatusCode());
        String json = String.valueOf(export.getBody());
        assertTrue(json.contains(me.email));
        assertTrue(json.contains("id=" + mine));
        assertFalse(json.contains(other.email));
        assertFalse(json.contains("id=" + theirs + ","), "someone else's parcel must not be in my export");
        assertEquals(HttpStatus.UNAUTHORIZED, call(null, HttpMethod.GET, "/me/export", null).getStatusCode());
    }

    // ---------------------------------------------------------------- deletion

    @Test
    void deletionIsBlockedWhileMoneyOrParcelsAreInMotionThenAnonymisesTheAccount() throws Exception {
        Acct trav = traveller("Carrier");
        Acct me = register("Leaver");
        long booking = acceptedBookingWithPhoto(trav, me);

        assertEquals(HttpStatus.BAD_REQUEST, call(me.token, HttpMethod.POST, "/me/delete", Map.of("password", "wrong")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, call(me.token, HttpMethod.POST, "/me/delete", Map.of("password", "password123")).getStatusCode(),
                "an accepted booking blocks deletion");

        deliver(trav, me, booking);
        assertEquals(HttpStatus.CONFLICT, call(me.token, HttpMethod.POST, "/me/delete", Map.of("password", "password123")).getStatusCode(),
                "delivered but the payment is still held");

        assertEquals(HttpStatus.OK, call(me.token, HttpMethod.POST, "/bookings/" + booking + "/confirm-receipt", null).getStatusCode());
        long openParcel = parcel(me); // a listing nobody has booked: simply withdrawn on deletion
        assertEquals(HttpStatus.OK, call(me.token, HttpMethod.POST, "/me/delete", Map.of("password", "password123")).getStatusCode());

        // the account is gone: old token and old credentials are rejected
        assertEquals(HttpStatus.UNAUTHORIZED, call(me.token, HttpMethod.GET, "/me", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(null, HttpMethod.POST, "/auth/login", Map.of("email", me.email, "password", "password123")).getStatusCode());

        // nothing identifying remains, but the other party's booking history is intact
        Map<String, Object> row = jdbc.queryForMap("SELECT email, full_name, phone, status FROM users WHERE id = ?", me.id);
        assertEquals("deleted-" + me.id + "@deleted.invalid", row.get("email"));
        assertEquals("Deleted user", row.get("full_name"));
        assertEquals(null, row.get("phone"));
        assertEquals("DELETED", row.get("status"));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM parcel WHERE id = ?", String.class, openParcel));
        assertEquals(null, jdbc.queryForObject("SELECT recipient_phone FROM parcel WHERE id = ?", String.class, openParcel));
        assertEquals("DELIVERED", call(trav.token, HttpMethod.GET, "/bookings/" + booking, null).getBody().get("status"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Long.class, me.id));
    }

    // ---------------------------------------------------------------- retention

    @Test
    void oldPhotosOfFinishedBookingsArePurgedButActiveBookingsKeepTheirs() throws Exception {
        Acct trav = traveller("Retainer");
        Acct sender = register("Keeper");
        long finished = acceptedBookingWithPhoto(trav, sender);
        deliver(trav, sender, finished);
        long active = acceptedBookingWithPhoto(traveller("Other"), register("Another"));
        Acct t2 = traveller("Fresh");
        Acct s2 = register("FreshSender");
        long recent = acceptedBookingWithPhoto(t2, s2);
        deliver(t2, s2, recent);

        // age two of the photos past the 180-day retention window
        jdbc.update("UPDATE evidence SET created_at = ? WHERE booking_id IN (?, ?)", java.sql.Timestamp.from(Instant.now().minus(200, ChronoUnit.DAYS)), finished, active);

        int purged = jobs.purgeOldEvidence();
        assertTrue(purged >= 1);
        assertEquals(0L, photoCount(finished), "old photo of a delivered booking is deleted");
        assertEquals(1L, photoCount(active), "old photo of a booking still in progress is kept");
        assertEquals(1L, photoCount(recent), "a recent photo is kept even though its booking is finished");
    }
}
