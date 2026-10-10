package com.parcelshare.identity;

import com.parcelshare.common.ApiException;
import com.parcelshare.common.AppProps;
import com.parcelshare.common.AuditService;
import com.parcelshare.identity.idp.IdentityProvider;
import com.parcelshare.identity.idp.NameMatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Verify with DigiLocker". Replaces the manual ID check for travellers: the user consents at the provider, we receive
 * a verified name and a reference id, and (if the name matches the account) the traveller is approved automatically.
 * Safety: single-use random state bound to the user, PKCE, 10-minute expiry, and one identity can only ever be linked
 * to one ParcelShare account.
 */
@RestController
@RequestMapping("/identity")
public class IdentityController {

    public record CompleteRequest(@NotBlank String code, @NotBlank String state) {
    }

    private record Pending(long userId, String verifier, Instant expires) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(10);

    // In memory is fine for one instance; a multi-instance deployment would move this to the database or Redis.
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

    private final ObjectProvider<IdentityProvider> providers;
    private final UserRepository users;
    private final AppProps props;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public IdentityController(ObjectProvider<IdentityProvider> providers, UserRepository users, AppProps props,
                              AuditService audit, TransactionTemplate tx) {
        this.providers = providers;
        this.users = users;
        this.props = props;
        this.audit = audit;
        this.tx = tx;
    }

    /** The real provider wins over the demo one when both exist. */
    private Optional<IdentityProvider> provider() {
        return providers.orderedStream().min(Comparator.comparing(p -> "DEMO".equals(p.name()) ? 1 : 0));
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        Optional<IdentityProvider> p = provider();
        return Map.of("available", p.isPresent(), "provider", p.map(IdentityProvider::name).orElse(""));
    }

    @PostMapping("/start")
    public Map<String, String> start(@AuthenticationPrincipal AuthUser me) {
        IdentityProvider p = provider().orElseThrow(() -> ApiException.notFound("Identity verification is not enabled yet"));
        pending.values().removeIf(x -> x.expires().isBefore(Instant.now()));
        String state = random(24);
        String verifier = random(48);
        pending.put(state, new Pending(me.id(), verifier, Instant.now().plus(TTL)));
        return Map.of("url", p.authorizeUrl(state, challenge(verifier), redirectUri()));
    }

    @PostMapping("/complete")
    public UserDto complete(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CompleteRequest req) {
        IdentityProvider p = provider().orElseThrow(() -> ApiException.notFound("Identity verification is not enabled yet"));
        Pending pend = pending.remove(req.state()); // single use: removed whether or not it turns out valid
        if (pend == null || pend.expires().isBefore(Instant.now()) || pend.userId() != me.id()) {
            throw ApiException.bad("This verification link has expired or is not valid. Please start again.");
        }
        IdentityProvider.VerifiedIdentity id = p.exchange(req.code(), pend.verifier(), redirectUri());
        return tx.execute(status -> link(me.id(), id));
    }

    private UserDto link(Long userId, IdentityProvider.VerifiedIdentity id) {
        if (users.existsByIdentityRefAndIdNot(id.reference(), userId)) {
            throw ApiException.conflict("This identity is already linked to another ParcelShare account.");
        }
        AppUser u = users.findById(userId).orElseThrow();
        u.setIdentityProvider(id.provider());
        u.setIdentityName(id.name());
        u.setIdentityRef(id.reference());
        u.setIdentityVerifiedAt(Instant.now());
        boolean same = NameMatch.matches(u.getFullName(), id.name());
        if (u.getTravellerVerification() != AppUser.Verification.APPROVED) {
            if (same) {
                u.setTravellerVerification(AppUser.Verification.APPROVED);
            } else {
                u.setTravellerVerification(AppUser.Verification.PENDING);
                u.setVerificationNote("Verified by " + id.provider() + " as \"" + id.name()
                        + "\", which differs from the account name. Admin to review.");
            }
        }
        audit.log(userId, "IDENTITY_VERIFIED", "user", userId, id.provider() + " nameMatch=" + same);
        return UserDto.of(u);
    }

    private String redirectUri() {
        return props.frontendUrl() + "/identity/callback";
    }

    private static String random(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
