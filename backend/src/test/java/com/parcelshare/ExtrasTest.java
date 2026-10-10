package com.parcelshare;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parcelshare.common.AppProps;
import com.parcelshare.notification.MailService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Photo evidence, email notifications and admin-editable policy. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:extras;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${TEST_DB_USER:sa}",
                "spring.datasource.password=${TEST_DB_PASSWORD:}",
                "parcelshare.rate-limit-per-minute=100000",
                "parcelshare.release-window-hours=48",
                "parcelshare.allow-dev-defaults=true",
                "parcelshare.admin-password=TestAdmin123!"
        })
class ExtrasTest {

    /** Records mail instead of sending it. */
    static class CapturingMail extends MailService {
        record Sent(String to, String subject, String body) {
        }

        private final List<Sent> sentMail = new CopyOnWriteArrayList<>();

        /** Read through a method: Spring proxies this bean (because of @Async), so direct field access would see null. */
        List<Sent> sent() {
            return sentMail;
        }

        CapturingMail(ObjectProvider<JavaMailSender> s, AppProps p) {
            super(s, p);
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
    JdbcTemplate jdbc;
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

    Acct register(String name) {
        String email = name.toLowerCase() + UUID.randomUUID().toString().substring(0, 6) + "@t.local";
        var r = call(null, HttpMethod.POST, "/auth/register", Map.of("email", email, "password", "password123", "fullName", name));
        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        @SuppressWarnings("unchecked")
        var u = (Map<String, Object>) r.getBody().get("user");
        return new Acct(((Number) u.get("id")).longValue(), (String) r.getBody().get("token"), email);
    }

    String admin() {
        return (String) call(null, HttpMethod.POST, "/auth/login",
                Map.of("email", "admin@parcelshare.local", "password", "TestAdmin123!")).getBody().get("token");
    }

    long idOf(String path, String name) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(admin());
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), LIST).getBody().stream()
                .filter(c -> name.equals(c.get("name"))).map(c -> ((Number) c.get("id")).longValue()).findFirst().orElseThrow();
    }

    /** A booking in ACCEPTED state, ready for handover. */
    record Setup(Acct traveller, Acct sender, long bookingId) {
    }

    Setup acceptedBooking() {
        Acct traveller = register("Trav");
        call(traveller.token, HttpMethod.POST, "/me/verification", Map.of("note", "ok"));
        call(admin(), HttpMethod.POST, "/admin/verifications/" + traveller.id + "/decision", Map.of("approve", true));
        Acct sender = register("Send");
        long delhi = idOf("/cities", "Delhi");
        long lucknow = idOf("/cities", "Lucknow");
        long tripId = ((Number) call(traveller.token, HttpMethod.POST, "/trips", Map.of("originCityId", delhi,
                "destinationCityId", lucknow, "departsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "travelMode", "BUS", "capacityGrams", 5000, "pricePerKgPaise", 10000)).getBody().get("id")).longValue();
        long parcelId = ((Number) call(sender.token, HttpMethod.POST, "/parcels", Map.of("originCityId", delhi,
                "destinationCityId", lucknow, "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "windowEnd", Instant.now().plus(3, ChronoUnit.DAYS).toString(), "categoryId", idOf("/parcels/categories", "Documents"),
                "weightGrams", 1000, "description", "Papers", "declaredValuePaise", 10000, "recipientName", "Mom")).getBody().get("id")).longValue();
        long bookingId = ((Number) call(sender.token, HttpMethod.POST, "/bookings", Map.of("tripId", tripId, "parcelId", parcelId))
                .getBody().get("id")).longValue();
        call(traveller.token, HttpMethod.POST, "/bookings/" + bookingId + "/accept", null);
        return new Setup(traveller, sender, bookingId);
    }

    static byte[] jpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 120, 200));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); // has transparency
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    ResponseEntity<Map<String, Object>> upload(String token, long bookingId, String kind, byte[] bytes) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("kind", kind);
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "photo.bin";
            }
        });
        return http.exchange("/bookings/" + bookingId + "/evidence", HttpMethod.POST, new HttpEntity<>(body, h), MAP);
    }

    // ------------------------------------------------------------ photo evidence

    @Test
    void photoEvidenceIsSanitisedAccessControlledAndBoundIntoTheEventChain() throws Exception {
        Setup s = acceptedBooking();

        // A big JPEG is accepted, shrunk, and re-encoded.
        var up = upload(s.sender.token, s.bookingId, "CONTENTS", jpeg(3000, 2000));
        assertEquals(HttpStatus.CREATED, up.getStatusCode(), String.valueOf(up.getBody()));
        long photoId = ((Number) up.getBody().get("id")).longValue();
        String recordedSha = (String) up.getBody().get("sha256");

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(s.traveller.token);
        var img = http.exchange("/bookings/" + s.bookingId + "/evidence/" + photoId + "/image", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertEquals(HttpStatus.OK, img.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, img.getHeaders().getContentType());
        byte[] stored = img.getBody();
        assertEquals(recordedSha, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stored)));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stored));
        assertTrue(Math.max(decoded.getWidth(), decoded.getHeight()) <= 1600, "downscaled to max 1600px");

        // PNG with transparency is flattened to JPEG.
        assertEquals(HttpStatus.CREATED, upload(s.traveller.token, s.bookingId, "CONTENTS", png(40, 40)).getStatusCode());

        // Non-images and disguised files are refused.
        assertEquals(HttpStatus.BAD_REQUEST, upload(s.sender.token, s.bookingId, "CONTENTS", "GIF89a not really".getBytes()).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, upload(s.sender.token, s.bookingId, "CONTENTS", "<script>alert(1)</script>".getBytes()).getStatusCode());
        // Valid JPEG header but garbage body.
        byte[] fake = new byte[200];
        fake[0] = (byte) 0xFF;
        fake[1] = (byte) 0xD8;
        fake[2] = (byte) 0xFF;
        assertEquals(HttpStatus.BAD_REQUEST, upload(s.sender.token, s.bookingId, "CONTENTS", fake).getStatusCode());

        // Rules: strangers out, sender cannot add delivery proof, wrong stage rejected.
        Acct stranger = register("Eve");
        assertEquals(HttpStatus.FORBIDDEN, upload(stranger.token, s.bookingId, "CONTENTS", jpeg(50, 50)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, upload(s.sender.token, s.bookingId, "DELIVERY", jpeg(50, 50)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, upload(s.traveller.token, s.bookingId, "DELIVERY", jpeg(50, 50)).getStatusCode());
        var strangerView = http.exchange("/bookings/" + s.bookingId + "/evidence/" + photoId + "/image", HttpMethod.GET,
                new HttpEntity<>(bearer(stranger.token)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, strangerView.getStatusCode());

        // The upload is in the timeline, and both integrity checks pass.
        var events = http.exchange("/bookings/" + s.bookingId + "/events", HttpMethod.GET, new HttpEntity<>(bearer(s.sender.token)), LIST).getBody();
        assertTrue(events.stream().anyMatch(e -> "EVIDENCE_ADDED".equals(e.get("type")) && String.valueOf(e.get("payload")).contains(recordedSha)));
        assertEquals(true, call(s.sender.token, HttpMethod.GET, "/bookings/" + s.bookingId + "/events/verify", null).getBody().get("valid"));
        assertEquals(true, call(s.sender.token, HttpMethod.GET, "/bookings/" + s.bookingId + "/evidence/verify", null).getBody().get("valid"));

        // Swap the image bytes AND its own hash column in the database: the event chain still exposes it.
        jdbc.update("UPDATE evidence SET data = ?, sha256 = ? WHERE id = ?", jpeg(60, 60), "0".repeat(64), photoId);
        var tampered = call(s.sender.token, HttpMethod.GET, "/bookings/" + s.bookingId + "/evidence/verify", null).getBody();
        assertEquals(false, tampered.get("valid"));
        assertTrue(String.valueOf(tampered.get("mismatchedPhotoIds")).contains(String.valueOf(photoId)));
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    // ------------------------------------------------------------ email notifications

    @Test
    void milestonesSendEmailsToTheRightPeople() {
        Setup s = acceptedBooking();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertTrue(mail.sent().stream().anyMatch(m -> m.to().equals(s.traveller.email) && m.subject().startsWith("New parcel request")),
                    "traveller should be told about the request");
            assertTrue(mail.sent().stream().anyMatch(m -> m.to().equals(s.sender.email) && m.subject().startsWith("Accepted:")),
                    "sender should be told about the acceptance");
        });
        // Nobody is emailed about their own action.
        assertFalse(mail.sent().stream().anyMatch(m -> m.to().equals(s.sender.email) && m.subject().startsWith("New parcel request")));
        // The email links to the booking page.
        var accepted = mail.sent().stream().filter(m -> m.to().equals(s.sender.email) && m.subject().startsWith("Accepted:")).findFirst().orElseThrow();
        assertTrue(accepted.body().contains("/bookings/" + s.bookingId));
    }

    @Test
    void registrationSendsAVerificationLinkThatWorks() {
        Acct a = register("Newuser");
        await().atMost(Duration.ofSeconds(5)).until(() -> mail.sent().stream().anyMatch(m -> m.to().equals(a.email)));
        var m = mail.sent().stream().filter(x -> x.to().equals(a.email)).findFirst().orElseThrow();
        assertEquals("Verify your ParcelShare email", m.subject());
        String token = m.body().substring(m.body().indexOf("token=") + 6).split("\\s")[0];
        assertNotNull(token);
        assertEquals(HttpStatus.OK, http.getForEntity("/auth/verify?token=" + token, Map.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.getForEntity("/auth/verify?token=" + token, Map.class).getStatusCode()); // single use
    }

    // ------------------------------------------------------------ admin-editable policy

    @Test
    void adminCanEditCategoriesAndKeywordsAndEveryChangeIsAudited() {
        String admin = admin();
        Acct sender = register("Pol");
        long delhi = idOf("/cities", "Delhi");
        long lucknow = idOf("/cities", "Lucknow");
        long docs = idOf("/parcels/categories", "Documents");
        java.util.function.Function<String, ResponseEntity<Map<String, Object>>> post = desc -> call(sender.token, HttpMethod.POST, "/parcels",
                Map.of("originCityId", delhi, "destinationCityId", lucknow,
                        "windowStart", Instant.now().plus(1, ChronoUnit.DAYS).toString(), "windowEnd", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                        "categoryId", docs, "weightGrams", 500, "description", desc, "declaredValuePaise", 1000, "recipientName", "R"));

        // Non-admins cannot touch policy.
        assertEquals(HttpStatus.FORBIDDEN, call(sender.token, HttpMethod.POST, "/admin/policy/keywords", Map.of("word", "x")).getStatusCode());

        // New blocked keyword takes effect immediately, and can be removed again.
        assertEquals(HttpStatus.CREATED, call(admin, HttpMethod.POST, "/admin/policy/keywords", Map.of("word", "Fireworks")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post.apply("box of fireworks").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, call(admin, HttpMethod.POST, "/admin/policy/keywords", Map.of("word", "fireworks")).getStatusCode());
        HttpHeaders ah = bearer(admin);
        long kwId = http.exchange("/admin/policy/keywords", HttpMethod.GET, new HttpEntity<>(ah), LIST).getBody().stream()
                .filter(k -> "fireworks".equals(k.get("word"))).map(k -> ((Number) k.get("id")).longValue()).findFirst().orElseThrow();
        assertEquals(HttpStatus.OK, call(admin, HttpMethod.DELETE, "/admin/policy/keywords/" + kwId, null).getStatusCode());
        assertEquals(HttpStatus.CREATED, post.apply("box of fireworks").getStatusCode());

        // Tighten a category limit: a 500 g parcel no longer fits a 100 g cap.
        var upd = call(admin, HttpMethod.PUT, "/admin/policy/categories/" + docs,
                Map.of("name", "Documents", "tier", "ALLOWED", "maxGrams", 100, "maxValuePaise", 300000, "active", true));
        assertEquals(HttpStatus.OK, upd.getStatusCode(), String.valueOf(upd.getBody()));
        assertEquals(HttpStatus.BAD_REQUEST, post.apply("light papers").getStatusCode());
        // ...and restore it.
        call(admin, HttpMethod.PUT, "/admin/policy/categories/" + docs,
                Map.of("name", "Documents", "tier", "ALLOWED", "maxGrams", 2000, "maxValuePaise", 300000, "active", true));
        assertEquals(HttpStatus.CREATED, post.apply("light papers").getStatusCode());

        var audit = http.exchange("/admin/audit", HttpMethod.GET, new HttpEntity<>(ah), LIST).getBody();
        for (String action : List.of("KEYWORD_ADDED", "KEYWORD_REMOVED", "CATEGORY_UPDATED")) {
            assertTrue(audit.stream().anyMatch(a -> action.equals(a.get("action"))), "audit has " + action);
        }
    }
}
