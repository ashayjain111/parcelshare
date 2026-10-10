package com.parcelshare.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionGuardTest {

    private static final String DEV_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";
    private static final String PG = "jdbc:postgresql://db.example.com/app?sslmode=require";

    private static AppProps props(String jwt, String adminPassword, boolean allowDev) {
        return new AppProps(jwt, 12, 10, 10, 48, 24, 30, false, List.of("http://localhost:5180"), "admin@x.test",
                adminPassword, false, false, "", "http://localhost:5180", allowDev, 180);
    }

    private static ProductionGuard guard(String dbUrl, AppProps p) {
        return new ProductionGuard(p, new MockEnvironment().withProperty("spring.datasource.url", dbUrl));
    }

    @Test
    void refusesTheBuiltInJwtSecretOnARealDatabase() {
        var g = guard(PG, props(DEV_SECRET, "my-own-strong-pass", false));
        var e = assertThrows(IllegalStateException.class, g::check);
        assertTrue(e.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void acceptsACustomSecretOnARealDatabase() {
        assertDoesNotThrow(guard(PG, props("a-long-random-production-secret-0123456789abcdef", "my-own-strong-pass", false))::check);
    }

    @Test
    void localH2DevelopmentKeepsItsConvenientDefaults() {
        var g = guard("jdbc:h2:file:./data/parcelshare", props(DEV_SECRET, ProductionGuard.DEV_ADMIN_PASSWORD, false));
        assertDoesNotThrow(g::check);
        assertTrue(g.isDevDatabase());
        assertTrue(g.knownAdminPasswordAllowed());
    }

    @Test
    void theKnownAdminPasswordIsNeverAllowedOnARealDatabase() {
        var g = guard(PG, props("a-long-random-production-secret-0123456789abcdef", ProductionGuard.DEV_ADMIN_PASSWORD, false));
        assertDoesNotThrow(g::check); // only warns; the admin account is simply not created
        assertFalse(g.knownAdminPasswordAllowed());
    }

    @Test
    void theExplicitTestEscapeHatchWorks() {
        var g = guard(PG, props(DEV_SECRET, ProductionGuard.DEV_ADMIN_PASSWORD, true));
        assertDoesNotThrow(g::check);
        assertTrue(g.knownAdminPasswordAllowed());
    }
}
