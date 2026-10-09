package com.parcelshare.identity;

import com.parcelshare.common.AppProps;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin on startup if ADMIN_PASSWORD is set and the account does not exist yet. */
@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AppProps props;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        if (props.adminPassword().isBlank() || users.existsByEmailIgnoreCase(props.adminEmail())) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setEmail(props.adminEmail().toLowerCase());
        admin.setFullName("Platform Admin");
        admin.setPasswordHash(encoder.encode(props.adminPassword()));
        admin.setRole(AppUser.Role.ADMIN);
        admin.setEmailVerified(true);
        admin.setTravellerVerification(AppUser.Verification.APPROVED);
        users.save(admin);
        log.info("Created admin account {}", admin.getEmail());
    }
}
