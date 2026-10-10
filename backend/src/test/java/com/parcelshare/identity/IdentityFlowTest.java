package com.parcelshare.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** The consent flow end to end, using the demo identity provider (which exists only in demo mode). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:identity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${TEST_DB_USER:sa}",
                "spring.datasource.password=${TEST_DB_PASSWORD:}",
                "parcelshare.demo-mode=true",
                "parcelshare.rate-limit-per-minute=100000",
                "parcelshare.allow-dev-defaults=true",
                "parcelshare.admin-password=TestAdmin123!"
        })
class IdentityFlowTest {

    @Autowired
    TestRestTemplate http;

    static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    record Acct(long id, String token) {
    }

    ResponseEntity<Map<String, Object>> call(String token, HttpMethod m, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        return http.exchange(path, m, new HttpEntity<>(body, h), MAP);
    }

    Acct register(String fullName) {
        var r = call(null, HttpMethod.POST, "/auth/register", Map.of("email", "id" + UUID.randomUUID() + "@t.local",
                "password", "password123", "fullName", fullName));
        @SuppressWarnings("unchecked")
        var u = (Map<String, Object>) r.getBody().get("user");
        return new Acct(((Number) u.get("id")).longValue(), (String) r.getBody().get("token"));
    }

    String startState(Acct a) {
        String url = (String) call(a.token, HttpMethod.POST, "/identity/start", null).getBody().get("url");
        assertTrue(url.contains("/digilocker-demo?state="), url);
        return url.substring(url.indexOf("state=") + 6);
    }

    ResponseEntity<Map<String, Object>> complete(Acct a, String code, String state) {
        return call(a.token, HttpMethod.POST, "/identity/complete", Map.of("code", code, "state", state));
    }

    long cityId(String name, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange("/cities", HttpMethod.GET, new HttpEntity<>(h), LIST).getBody().stream()
                .filter(c -> name.equals(c.get("name"))).map(c -> ((Number) c.get("id")).longValue()).findFirst().orElseThrow();
    }

    @Test
    void optionsReportTheDemoProviderAndStartNeedsLogin() {
        Acct a = register("Options Tester");
        assertEquals(HttpStatus.UNAUTHORIZED, call(null, HttpMethod.GET, "/identity/options", null).getStatusCode());
        var o = call(a.token, HttpMethod.GET, "/identity/options", null).getBody();
        assertEquals(true, o.get("available"));
        assertEquals("DEMO", o.get("provider"));
        assertEquals(HttpStatus.UNAUTHORIZED, call(null, HttpMethod.POST, "/identity/start", null).getStatusCode());
    }

    @Test
    void aMatchingNameVerifiesTheTravellerWithNoAdminAndUnlocksPostingTrips() {
        Acct a = register("Meera Kapoor");
        // before verification a traveller cannot post a trip
        Map<String, Object> trip = Map.of("originCityId", cityId("Delhi", a.token), "destinationCityId", cityId("Lucknow", a.token),
                "departsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "travelMode", "BUS", "capacityGrams", 3000,
                "pricePerKgPaise", 9000);
        assertEquals(HttpStatus.FORBIDDEN, call(a.token, HttpMethod.POST, "/trips", trip).getStatusCode());

        var done = complete(a, "demo:MEERA KAPOOR", startState(a));
        assertEquals(HttpStatus.OK, done.getStatusCode(), String.valueOf(done.getBody()));
        assertEquals("APPROVED", done.getBody().get("travellerVerification"));
        assertEquals(true, done.getBody().get("identityVerified"));
        assertEquals("DEMO", done.getBody().get("identityProvider"));

        assertEquals(HttpStatus.CREATED, call(a.token, HttpMethod.POST, "/trips", trip).getStatusCode());
    }

    @Test
    void aDifferentNameGoesToAnAdminInsteadOfBeingApproved() {
        Acct a = register("Karan Mehta");
        var done = complete(a, "demo:Someone Else Entirely", startState(a));
        assertEquals("PENDING", done.getBody().get("travellerVerification"));
        assertEquals(true, done.getBody().get("identityVerified"));
    }

    @Test
    void theStateIsSingleUseAndBoundToTheUserWhoStartedIt() {
        Acct a = register("Anil Rao");
        Acct b = register("Bhavna Rao");
        String state = startState(a);

        assertEquals(HttpStatus.BAD_REQUEST, complete(b, "demo:Anil Rao", state).getStatusCode(), "another user cannot use my state");
        assertEquals(HttpStatus.BAD_REQUEST, complete(a, "demo:Anil Rao", state).getStatusCode(),
                "and trying it burned the state, so even the owner must start again");
        assertEquals(HttpStatus.OK, complete(a, "demo:Anil Rao", startState(a)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, complete(a, "demo:Anil Rao", "not-a-real-state").getStatusCode());
    }

    @Test
    void oneIdentityCanOnlyBeLinkedToOneAccount() {
        Acct a = register("Shared Person");
        Acct b = register("Shared Person");
        assertEquals(HttpStatus.OK, complete(a, "demo:Shared Person", startState(a)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, complete(b, "demo:Shared Person", startState(b)).getStatusCode());
    }

    @Test
    void malformedProviderCodesAreRejected() {
        Acct a = register("Code Tester");
        assertEquals(HttpStatus.BAD_REQUEST, complete(a, "garbage", startState(a)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, complete(a, "demo:   ", startState(a)).getStatusCode());
    }
}
