package com.parcelshare.identity.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parcelshare.common.ApiException;
import com.parcelshare.common.AppProps;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Checks the real DigiLocker client against a fake government server running inside the test. */
class DigiLockerProviderTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> lastTokenBody = new AtomicReference<>();
    private final AtomicReference<String> lastUserAuth = new AtomicReference<>();
    private volatile String userJson = "{\"digilockerid\":\"dl-123\",\"name\":\"Priya Singh\"}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastTokenBody.set(body);
            byte[] out = (body.contains("code=good") ? "{\"access_token\":\"tok-1\"}" : "{\"error\":\"invalid_grant\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(body.contains("code=good") ? 200 : 400, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.createContext("/user", ex -> {
            lastUserAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = userJson.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private DigiLockerProvider provider() {
        return new DigiLockerProvider(new AppProps("x".repeat(40), 12, 10, 10, 48, 24, 30, false, List.of(), "a@b.c", "", false, false,
                "", "https://app.example", false, 180, "my-client", "my-secret", base + "/authorize", base + "/token", base + "/user"));
    }

    @Test
    void theAuthorizeUrlCarriesPkceAndStateButNeverTheSecret() {
        String url = provider().authorizeUrl("st-1", "chal-1", "https://app.example/identity/callback");
        assertTrue(url.startsWith(base + "/authorize?response_type=code"));
        assertTrue(url.contains("client_id=my-client"));
        assertTrue(url.contains("state=st-1"));
        assertTrue(url.contains("code_challenge=chal-1&code_challenge_method=S256"));
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fapp.example%2Fidentity%2Fcallback"));
        assertTrue(!url.contains("my-secret"), "the client secret must never appear in a browser URL");
    }

    @Test
    void exchangeTradesTheCodeForAVerifiedNameAndReference() {
        var id = provider().exchange("good", "verifier-1", "https://app.example/identity/callback");
        assertEquals("DIGILOCKER", id.provider());
        assertEquals("Priya Singh", id.name());
        assertEquals("dl-123", id.reference());
        assertTrue(lastTokenBody.get().contains("grant_type=authorization_code"));
        assertTrue(lastTokenBody.get().contains("code_verifier=verifier-1"));
        assertTrue(lastTokenBody.get().contains("client_secret=my-secret"), "the secret goes server-to-server only");
        assertEquals("Bearer tok-1", lastUserAuth.get());
    }

    @Test
    void aRejectedCodeBecomesAFriendlyError() {
        var e = assertThrows(ApiException.class, () -> provider().exchange("bad", "v", "https://app.example/identity/callback"));
        assertTrue(e.getMessage().contains("DigiLocker"));
    }

    @Test
    void aResponseWithoutAVerifiedNameIsNotTrusted() {
        userJson = "{\"digilockerid\":\"dl-999\"}";
        assertThrows(ApiException.class, () -> provider().exchange("good", "v", "https://app.example/identity/callback"));
    }

    @Test
    void anUnreachableProviderIsReportedAsABadGatewayNotACrash() {
        server.stop(0);
        var e = assertThrows(ApiException.class, () -> provider().exchange("good", "v", "https://app.example/identity/callback"));
        assertEquals(502, e.getStatus().value());
    }
}
