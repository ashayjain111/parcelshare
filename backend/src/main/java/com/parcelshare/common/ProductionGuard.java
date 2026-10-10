package com.parcelshare.common;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with publicly known development secrets when the app is pointed at a real (non-H2) database.
 * Local development (file or in-memory H2) is unaffected. Set ALLOW_DEV_DEFAULTS=true only for automated tests.
 */
@Component
public class ProductionGuard {

    public static final String DEV_JWT_PREFIX = "dev-only-secret";
    public static final String DEV_ADMIN_PASSWORD = "ChangeMe123!";

    private static final Logger log = LoggerFactory.getLogger(ProductionGuard.class);

    private final AppProps props;
    private final Environment env;

    public ProductionGuard(AppProps props, Environment env) {
        this.props = props;
        this.env = env;
    }

    /** True when running on the zero-install local H2 database. */
    public boolean isDevDatabase() {
        return env.getProperty("spring.datasource.url", "").startsWith("jdbc:h2:");
    }

    /** True if a seeded/known admin password may be used (dev database, or explicitly allowed for tests). */
    public boolean knownAdminPasswordAllowed() {
        return isDevDatabase() || props.allowDevDefaults();
    }

    @PostConstruct
    void check() {
        if (isDevDatabase() || props.allowDevDefaults()) {
            return;
        }
        if (props.jwtSecret().startsWith(DEV_JWT_PREFIX)) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Refusing to start a production database with the built-in development secret. "
                            + "Set JWT_SECRET to a random string of 40+ characters.");
        }
        if (DEV_ADMIN_PASSWORD.equals(props.adminPassword())) {
            log.warn("ADMIN_PASSWORD is the development default: no admin account will be created. Set ADMIN_PASSWORD to your own value.");
        }
    }
}
