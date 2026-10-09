package com.parcelshare.parcel;

import com.parcelshare.common.ApiException;
import com.parcelshare.geo.City;
import com.parcelshare.geo.CityRepository;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.identity.UserRepository;
import com.parcelshare.matching.MatchingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/parcels")
@RequiredArgsConstructor
public class ParcelController {

    public record CreateParcelRequest(@NotNull Long originCityId, @NotNull Long destinationCityId,
                                      @NotNull Instant windowStart, @NotNull Instant windowEnd,
                                      @NotNull Long categoryId,
                                      @Min(50) @Max(30000) int weightGrams,
                                      @NotBlank @Size(max = 500) String description,
                                      @Min(0) long declaredValuePaise,
                                      @NotBlank @Size(max = 120) String recipientName,
                                      @Size(max = 20) String recipientPhone) {
    }

    public record CategoryDto(Long id, String name, String tier, int maxGrams, long maxValuePaise) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParcelRepository parcels;
    private final ParcelCategoryRepository categories;
    private final CityRepository cities;
    private final UserRepository users;
    private final PolicyService policy;
    private final MatchingService matching;

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categories.findByActiveTrueOrderByTierAscNameAsc().stream()
                .map(c -> new CategoryDto(c.getId(), c.getName(), c.getTier().name(), c.getMaxGrams(), c.getMaxValuePaise()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ParcelView create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateParcelRequest req) {
        AppUser sender = users.findById(me.id()).orElseThrow();
        if (!sender.isEmailVerified()) {
            throw ApiException.forbidden("Please verify your email first");
        }
        City from = cities.findById(req.originCityId()).orElseThrow(() -> ApiException.bad("Unknown origin city"));
        City to = cities.findById(req.destinationCityId()).orElseThrow(() -> ApiException.bad("Unknown destination city"));
        if (from.getRegion().getId().equals(to.getRegion().getId())) {
            throw ApiException.bad("Origin and destination must be in different areas");
        }
        if (!req.windowEnd().isAfter(req.windowStart()) || req.windowEnd().isBefore(Instant.now())) {
            throw ApiException.bad("Pick a valid future pickup window");
        }
        ParcelCategory category = categories.findById(req.categoryId())
                .orElseThrow(() -> ApiException.bad("Unknown category"));
        PolicyService.Decision decision = policy.evaluate(category, req.weightGrams(), req.declaredValuePaise(),
                req.description());

        Parcel p = new Parcel();
        p.setPublicCode(newPublicCode());
        p.setSender(sender);
        p.setOrigin(from);
        p.setDestination(to);
        p.setWindowStart(req.windowStart());
        p.setWindowEnd(req.windowEnd());
        p.setCategory(category);
        p.setWeightGrams(req.weightGrams());
        p.setDescription(req.description().trim());
        p.setDeclaredValuePaise(req.declaredValuePaise());
        p.setRecipientName(req.recipientName().trim());
        p.setRecipientPhone(req.recipientPhone());
        p.setStatus(decision.status());
        p.setFlagReason(decision.flagReason());
        return ParcelView.of(parcels.save(p), true);
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<ParcelView> mine(@AuthenticationPrincipal AuthUser me) {
        return parcels.findBySenderIdOrderByCreatedAtDesc(me.id()).stream().map(p -> ParcelView.of(p, true)).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ParcelView get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Parcel p = owned(me, id);
        return ParcelView.of(p, true);
    }

    @GetMapping("/{id}/matches")
    @Transactional(readOnly = true)
    public List<MatchingService.TripMatch> matches(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Parcel p = owned(me, id);
        if (p.getStatus() != Parcel.Status.OPEN) {
            return List.of();
        }
        return matching.tripsForParcel(p);
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ParcelView cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Parcel p = owned(me, id);
        if (p.getStatus() != Parcel.Status.OPEN && p.getStatus() != Parcel.Status.PENDING_REVIEW) {
            throw ApiException.conflict("Only unbooked parcels can be cancelled here. Cancel the booking instead.");
        }
        p.setStatus(Parcel.Status.CANCELLED);
        return ParcelView.of(p, true);
    }

    private Parcel owned(AuthUser me, Long id) {
        Parcel p = parcels.findById(id).orElseThrow(() -> ApiException.notFound("Parcel not found"));
        if (!p.getSender().getId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("This is not your parcel");
        }
        return p;
    }

    /** Human-friendly id such as PCL-82931, regenerated on the (rare) collision. */
    private String newPublicCode() {
        for (int i = 0; i < 10; i++) {
            String code = "PCL-" + (10000 + RANDOM.nextInt(90000));
            if (!parcels.existsByPublicCode(code)) {
                return code;
            }
        }
        return "PCL-" + (100000 + RANDOM.nextInt(900000));
    }
}
