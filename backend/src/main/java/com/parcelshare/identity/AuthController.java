package com.parcelshare.identity;

import com.parcelshare.common.ApiException;
import com.parcelshare.common.AppProps;
import com.parcelshare.notification.MailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 120) String fullName,
                                  @Size(max = 20) String phone) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, UserDto user) {
    }

    public record ForgotRequest(@NotBlank @Email String email) {
    }

    public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String password) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProps props;
    private final MailService mail;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setEmailVerified(!props.requireEmailVerification());
        u.setVerifyToken(UUID.randomUUID().toString().replace("-", ""));
        users.save(u);
        String link = props.frontendUrl() + "/verify?token=" + u.getVerifyToken();
        log.info("Email verification link for {}: {}", email, link);
        mail.send(email, "Verify your ParcelShare email",
                "Hi " + u.getFullName() + ",\n\nWelcome to ParcelShare. Please confirm your email address:\n" + link
                        + "\n\nIf you did not sign up, you can ignore this message.");
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(jwt.issue(u), UserDto.of(u)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (u.getStatus() != AppUser.Status.ACTIVE) {
            throw ApiException.forbidden("This account is suspended");
        }
        return new AuthResponse(jwt.issue(u), UserDto.of(u));
    }

    /**
     * Always answers the same way, whether or not the address exists, so the endpoint cannot be used to discover
     * who has an account. The emailed link is single-use and valid for one hour; only its hash is stored.
     */
    @PostMapping("/forgot")
    @Transactional
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest req) {
        users.findByEmailIgnoreCase(req.email().trim()).filter(u -> u.getStatus() == AppUser.Status.ACTIVE).ifPresent(u -> {
            String token = Tokens.random();
            u.setResetTokenHash(Tokens.sha256(token));
            u.setResetExpiresAt(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS));
            String link = props.frontendUrl() + "/reset?token=" + token;
            log.info("Password reset link for {}: {}", u.getEmail(), link);
            mail.send(u.getEmail(), "Reset your ParcelShare password",
                    "Hi " + u.getFullName() + ",\n\nUse this link to choose a new password (valid for 1 hour):\n" + link
                            + "\n\nIf you did not ask for this, you can ignore this message. Your password has not changed.");
        });
        return Map.of("status", "If that address has an account, a reset link is on its way.");
    }

    @PostMapping("/reset")
    @Transactional
    public Map<String, String> reset(@Valid @RequestBody ResetRequest req) {
        AppUser u = users.findByResetTokenHash(Tokens.sha256(req.token()))
                .filter(x -> x.getResetExpiresAt() != null && x.getResetExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> ApiException.bad("This reset link is invalid or has expired"));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setResetTokenHash(null);
        u.setResetExpiresAt(null);
        u.setTokenVersion(u.getTokenVersion() + 1);
        return Map.of("status", "Password updated. You can log in now.");
    }

    @GetMapping("/verify")
    @Transactional
    public Map<String, String> verify(@RequestParam String token) {
        AppUser u = users.findByVerifyToken(token).orElseThrow(() -> ApiException.bad("Invalid verification link"));
        u.setEmailVerified(true);
        u.setVerifyToken(null);
        return Map.of("status", "verified");
    }
}
