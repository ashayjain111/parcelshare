package com.parcelshare.trip;

import com.parcelshare.common.ApiException;
import com.parcelshare.identity.AuthUser;
import com.parcelshare.notification.NotificationService;
import com.parcelshare.parcel.Parcel;
import com.parcelshare.parcel.ParcelRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Traveller-initiated side of the marketplace: tell a sender "I can carry this". */
@RestController
@RequestMapping("/trips/{tripId}/offers")
@RequiredArgsConstructor
public class TripOfferController {

    public record OfferRequest(@NotNull Long parcelId) {
    }

    private final TripRepository trips;
    private final ParcelRepository parcels;
    private final ParcelOfferRepository offers;
    private final NotificationService notifications;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> offer(@AuthenticationPrincipal AuthUser me, @PathVariable Long tripId,
                                     @Valid @RequestBody OfferRequest req) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
        if (!trip.getTraveller().getId().equals(me.id())) {
            throw ApiException.forbidden("This is not your trip");
        }
        if (trip.getStatus() != Trip.Status.OPEN || !trip.getDepartsAt().isAfter(Instant.now())) {
            throw ApiException.conflict("This trip is no longer open");
        }
        Parcel p = parcels.findById(req.parcelId()).orElseThrow(() -> ApiException.notFound("Parcel not found"));
        boolean fits = p.getStatus() == Parcel.Status.OPEN
                && !p.getSender().getId().equals(me.id())
                && p.getOrigin().getRegion().getId().equals(trip.getOrigin().getRegion().getId())
                && p.getDestination().getRegion().getId().equals(trip.getDestination().getRegion().getId())
                && !trip.getDepartsAt().isBefore(p.getWindowStart()) && !trip.getDepartsAt().isAfter(p.getWindowEnd())
                && p.getWeightGrams() <= trip.remainingGrams();
        if (!fits) {
            throw ApiException.bad("That parcel does not match this trip");
        }
        if (offers.existsByTripIdAndParcelId(tripId, p.getId())) {
            throw ApiException.conflict("You already offered to carry this parcel");
        }
        offers.save(new ParcelOffer(tripId, p.getId()));
        String route = trip.getOrigin().getName() + " to " + trip.getDestination().getName();
        notifications.notify(p.getSender(), "OFFER_RECEIVED", "A traveller can carry " + p.getPublicCode(),
                trip.getTraveller().getFullName() + " is travelling " + route + " and offered to carry your parcel. "
                        + "Review the offer and book them if you like it.", "/parcels/" + p.getId());
        return Map.of("offered", true);
    }
}
