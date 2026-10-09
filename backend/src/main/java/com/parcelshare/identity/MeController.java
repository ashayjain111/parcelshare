package com.parcelshare.identity;

import com.parcelshare.common.ApiException;
import com.parcelshare.rating.Rating;
import com.parcelshare.rating.RatingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    public record VerificationRequest(@NotBlank @Size(max = 500) String note) {
    }

    public record ProfileRequest(@NotBlank @Size(max = 120) String fullName, @Size(max = 20) String phone) {
    }

    public record PasswordRequest(@NotBlank String current, @NotBlank @Size(min = 8, max = 72) String next) {
    }

    public record ReviewView(int score, String comment, String fromName, Instant createdAt) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RatingRepository ratings;

    @GetMapping
    @Transactional(readOnly = true)
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return UserDto.of(users.findById(me.id()).orElseThrow());
    }

    @PatchMapping
    @Transactional
    public UserDto updateProfile(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ProfileRequest req) {
        AppUser u = users.findById(me.id()).orElseThrow();
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        return UserDto.of(u);
    }

    @PostMapping("/password")
    @Transactional
    public Map<String, String> changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PasswordRequest req) {
        AppUser u = users.findById(me.id()).orElseThrow();
        if (!encoder.matches(req.current(), u.getPasswordHash())) {
            throw ApiException.bad("Your current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(req.next()));
        return Map.of("status", "Password changed");
    }

    /** What other people said about you (most recent first). */
    @GetMapping("/reviews")
    @Transactional(readOnly = true)
    public List<ReviewView> reviews(@AuthenticationPrincipal AuthUser me) {
        List<Rating> list = ratings.findTop30ByRateeIdOrderByIdDesc(me.id());
        Map<Long, String> names = users.findAllById(list.stream().map(Rating::getRaterId).distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u.getFullName().split(" ")[0], (a, b) -> a, java.util.HashMap::new));
        return list.stream().map(r -> new ReviewView(r.getScore(), r.getComment(), names.getOrDefault(r.getRaterId(), "A user"),
                r.getCreatedAt())).toList();
    }

    /**
     * Traveller verification request. In the MVP an admin reviews it manually
     * (e.g. against an ID + selfie shared out-of-band). No ID image is stored by this service.
     */
    @PostMapping("/verification")
    @Transactional
    public UserDto requestVerification(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody VerificationRequest req) {
        AppUser u = users.findById(me.id()).orElseThrow();
        if (u.getTravellerVerification() == AppUser.Verification.APPROVED) {
            throw ApiException.conflict("You are already verified");
        }
        u.setTravellerVerification(AppUser.Verification.PENDING);
        u.setVerificationNote(req.note());
        return UserDto.of(u);
    }
}
