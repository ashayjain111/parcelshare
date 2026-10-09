package com.parcelshare.trip;

import com.parcelshare.common.ApiException;
import com.parcelshare.geo.City;
import com.parcelshare.geo.CityRepository;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.identity.UserRepository;
import com.parcelshare.booking.Booking;
import com.parcelshare.booking.BookingRepository;
import com.parcelshare.booking.BookingService;
import com.parcelshare.booking.BookingStatus;
import com.parcelshare.matching.MatchingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    public record CreateTripRequest(@NotNull Long originCityId, @NotNull Long destinationCityId,
                                    @NotNull Instant departsAt, @NotNull Trip.Mode travelMode,
                                    @Min(500) @Max(30000) int capacityGrams,
                                    @Min(1000) @Max(200000) int pricePerKgPaise) {
    }

    private final TripRepository trips;
    private final CityRepository cities;
    private final UserRepository users;
    private final MatchingService matching;
    private final BookingRepository bookings;
    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TripView create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateTripRequest req) {
        AppUser u = users.findById(me.id()).orElseThrow();
        if (u.getTravellerVerification() != AppUser.Verification.APPROVED) {
            throw ApiException.forbidden("Only verified travellers can post trips. Request verification from your profile.");
        }
        if (!u.isEmailVerified()) {
            throw ApiException.forbidden("Please verify your email first");
        }
        City from = cities.findById(req.originCityId()).orElseThrow(() -> ApiException.bad("Unknown origin city"));
        City to = cities.findById(req.destinationCityId()).orElseThrow(() -> ApiException.bad("Unknown destination city"));
        if (from.getRegion().getId().equals(to.getRegion().getId())) {
            throw ApiException.bad("Origin and destination must be in different areas");
        }
        if (req.departsAt().isBefore(Instant.now().plus(1, ChronoUnit.HOURS))) {
            throw ApiException.bad("Departure must be at least 1 hour from now");
        }
        Trip t = new Trip();
        t.setTraveller(u);
        t.setOrigin(from);
        t.setDestination(to);
        t.setDepartsAt(req.departsAt());
        t.setTravelMode(req.travelMode());
        t.setCapacityGrams(req.capacityGrams());
        t.setPricePerKgPaise(req.pricePerKgPaise());
        return TripView.of(trips.save(t));
    }

    /** Browse upcoming open trips. Optional filters: from, to (city ids) and date (yyyy-MM-dd, UTC day). */
    @GetMapping
    @Transactional(readOnly = true)
    public List<TripView> browse(@RequestParam(required = false) Long from, @RequestParam(required = false) Long to,
                                 @RequestParam(required = false) LocalDate date) {
        Instant start = date == null ? Instant.now() : date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = date == null ? Instant.now().plus(60, ChronoUnit.DAYS)
                : date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return trips.searchByDate(start, end, Trip.Status.OPEN).stream()
                .filter(t -> t.remainingGrams() > 0)
                .filter(t -> from == null || t.getOrigin().getId().equals(from))
                .filter(t -> to == null || t.getDestination().getId().equals(to))
                .map(TripView::of).toList();
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<TripView> mine(@AuthenticationPrincipal AuthUser me) {
        return trips.findByTravellerIdOrderByDepartsAtDesc(me.id()).stream().map(TripView::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public TripView get(@PathVariable Long id) {
        return TripView.of(trips.findById(id).orElseThrow(() -> ApiException.notFound("Trip not found")));
    }

    @GetMapping("/{id}/matches")
    @Transactional(readOnly = true)
    public List<MatchingService.ParcelMatch> matches(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Trip t = trips.findById(id).orElseThrow(() -> ApiException.notFound("Trip not found"));
        if (!t.getTraveller().getId().equals(me.id())) {
            throw ApiException.forbidden("This is not your trip");
        }
        return matching.parcelsForTrip(t);
    }

    /**
     * A traveller can cancel a trip until a parcel has been handed over. Requests and accepted bookings on it are
     * cancelled with full refunds to the senders (an accepted booking counts against the traveller's reliability).
     */
    @PostMapping("/{id}/cancel")
    @Transactional
    public TripView cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Trip t = trips.findById(id).orElseThrow(() -> ApiException.notFound("Trip not found"));
        if (!t.getTraveller().getId().equals(me.id())) {
            throw ApiException.forbidden("This is not your trip");
        }
        if (t.getStatus() != Trip.Status.OPEN) {
            throw ApiException.conflict("This trip is already " + t.getStatus());
        }
        List<Booking> all = bookings.findByTripId(id);
        boolean inProgress = all.stream().anyMatch(b -> switch (b.getStatus()) {
            case HANDED_OVER, IN_TRANSIT, DELIVERY_FAILED, DISPUTED -> true;
            default -> false;
        });
        if (inProgress) {
            throw ApiException.conflict("A parcel is already with you on this trip. Finish or dispute those bookings first.");
        }
        for (Booking b : all) {
            if (b.getStatus() == BookingStatus.REQUESTED || b.getStatus() == BookingStatus.ACCEPTED) {
                bookingService.cancel(me, b.getId());
            }
        }
        t.setStatus(Trip.Status.CANCELLED);
        return TripView.of(t);
    }
}
