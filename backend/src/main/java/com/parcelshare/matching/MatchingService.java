package com.parcelshare.matching;

import com.parcelshare.common.AppProps;
import com.parcelshare.identity.AppUser;
import com.parcelshare.identity.Reputation;
import com.parcelshare.parcel.Parcel;
import com.parcelshare.parcel.ParcelRepository;
import com.parcelshare.parcel.ParcelView;
import com.parcelshare.payment.Pricing;
import com.parcelshare.trip.Trip;
import com.parcelshare.trip.TripRepository;
import com.parcelshare.trip.ParcelOffer;
import com.parcelshare.trip.ParcelOfferRepository;
import com.parcelshare.trip.TripView;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule-based matching. Step 1 (hard constraints) is a SQL query; step 2 ranks survivors with {@link MatchScorer}.
 * At the target scale (hundreds of parcels a month) this is instant. See docs for the scaling path.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    public record TripMatch(TripView trip, int score, Map<String, Integer> breakdown, long quotePaise, boolean offered) {
    }

    public record ParcelMatch(ParcelView parcel, int score, Map<String, Integer> breakdown, long earningsPaise, boolean offered) {
    }

    private final TripRepository trips;
    private final ParcelRepository parcels;
    private final ParcelOfferRepository offers;
    private final AppProps props;

    @Transactional(readOnly = true)
    public List<TripMatch> tripsForParcel(Parcel p) {
        List<Trip> candidates = trips.candidatesForParcel(p.getOrigin().getRegion().getId(),
                p.getDestination().getRegion().getId(), p.getWindowStart(), p.getWindowEnd(), Instant.now(),
                p.getWeightGrams(), p.getSender().getId(), Trip.Status.OPEN, AppUser.Verification.APPROVED,
                AppUser.Status.ACTIVE);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> quotes = candidates.stream()
                .map(t -> Pricing.quote(t.getPricePerKgPaise(), p.getWeightGrams())).toList();
        java.util.Set<Long> offeredTrips = offers.findByParcelId(p.getId()).stream().map(ParcelOffer::getTripId)
                .collect(java.util.stream.Collectors.toSet());
        long min = quotes.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = quotes.stream().mapToLong(Long::longValue).max().orElse(0);

        return java.util.stream.IntStream.range(0, candidates.size()).mapToObj(i -> {
            Trip t = candidates.get(i);
            AppUser tr = t.getTraveller();
            var result = MatchScorer.score(new MatchScorer.Inputs(
                    t.getOrigin().getId().equals(p.getOrigin().getId()),
                    t.getDestination().getId().equals(p.getDestination().getId()),
                    MatchScorer.timeFit(t.getDepartsAt(), p.getWindowStart(), p.getWindowEnd()),
                    tr.ratingAvg(),
                    Reputation.reliability(tr.getCompletedDeliveries(), tr.getCancelledCount(), tr.getDisputedCount()),
                    MatchScorer.normalise(quotes.get(i), min, max, false),
                    MatchScorer.capacityFit(p.getWeightGrams(), t.remainingGrams())));
            return new TripMatch(TripView.of(t), result.score(), result.breakdown(), quotes.get(i), offeredTrips.contains(t.getId()));
        }).sorted(Comparator.comparingInt(TripMatch::score).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<ParcelMatch> parcelsForTrip(Trip t) {
        if (t.getStatus() != Trip.Status.OPEN) {
            return List.of();
        }
        List<Parcel> candidates = parcels.candidatesForTrip(t.getOrigin().getRegion().getId(),
                t.getDestination().getRegion().getId(), t.getDepartsAt(), t.remainingGrams(),
                t.getTraveller().getId(), Parcel.Status.OPEN);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> earnings = candidates.stream().map(p -> {
            long price = Pricing.quote(t.getPricePerKgPaise(), p.getWeightGrams());
            return price - Pricing.commission(price, props.commissionPercent());
        }).toList();
        java.util.Set<Long> offeredParcels = offers.findByTripId(t.getId()).stream().map(ParcelOffer::getParcelId)
                .collect(java.util.stream.Collectors.toSet());
        long min = earnings.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = earnings.stream().mapToLong(Long::longValue).max().orElse(0);

        return java.util.stream.IntStream.range(0, candidates.size()).mapToObj(i -> {
            Parcel p = candidates.get(i);
            AppUser s = p.getSender();
            var result = MatchScorer.score(new MatchScorer.Inputs(
                    t.getOrigin().getId().equals(p.getOrigin().getId()),
                    t.getDestination().getId().equals(p.getDestination().getId()),
                    MatchScorer.timeFit(t.getDepartsAt(), p.getWindowStart(), p.getWindowEnd()),
                    s.ratingAvg(),
                    Reputation.reliability(s.getCompletedDeliveries(), s.getCancelledCount(), s.getDisputedCount()),
                    MatchScorer.normalise(earnings.get(i), min, max, true),
                    MatchScorer.capacityFit(p.getWeightGrams(), t.remainingGrams())));
            return new ParcelMatch(ParcelView.of(p, false), result.score(), result.breakdown(), earnings.get(i), offeredParcels.contains(p.getId()));
        }).sorted(Comparator.comparingInt(ParcelMatch::score).reversed()).toList();
    }
}
