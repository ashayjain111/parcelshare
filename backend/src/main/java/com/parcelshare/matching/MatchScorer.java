package com.parcelshare.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Soft-factor scoring for candidates that have already passed the hard constraints (see TripRepository
 * / ParcelRepository). Pure functions, no I/O, so it is trivially unit-testable and tunable.
 *
 * Weights sum to 100: route 40, time 15, rating 15, reliability 15, price 10, capacity 5.
 */
public final class MatchScorer {

    static final double W_ROUTE = 40, W_TIME = 15, W_RATING = 15, W_RELIABILITY = 15, W_PRICE = 10, W_CAPACITY = 5;

    private MatchScorer() {
    }

    public record Inputs(boolean exactOrigin, boolean exactDestination, double timeFit, double smoothedRating,
                         double reliability, double priceScore, double capacityFit) {
    }

    public record Result(int score, Map<String, Integer> breakdown) {
    }

    public static Result score(Inputs in) {
        // Both ends exact = 40; one end exact (other is same region, e.g. Noida vs Delhi) = 28; region-only = 20.
        double route = in.exactOrigin() && in.exactDestination() ? W_ROUTE
                : (in.exactOrigin() || in.exactDestination()) ? 28 : 20;
        double time = W_TIME * clamp(in.timeFit());
        double rating = W_RATING * clamp((in.smoothedRating() - 1) / 4.0);
        double reliability = W_RELIABILITY * clamp(in.reliability());
        double price = W_PRICE * clamp(in.priceScore());
        double capacity = W_CAPACITY * clamp(in.capacityFit());

        Map<String, Integer> parts = new LinkedHashMap<>();
        parts.put("route", (int) Math.round(route));
        parts.put("time", (int) Math.round(time));
        parts.put("rating", (int) Math.round(rating));
        parts.put("reliability", (int) Math.round(reliability));
        parts.put("price", (int) Math.round(price));
        parts.put("capacity", (int) Math.round(capacity));
        int total = (int) Math.round(route + time + rating + reliability + price + capacity);
        return new Result(total, parts);
    }

    /** 1.0 when departure is at the middle of the sender's window, falling to 0.4 at the edges. */
    public static double timeFit(Instant departs, Instant windowStart, Instant windowEnd) {
        long half = Math.max(Duration.ofHours(1).toMillis(), Duration.between(windowStart, windowEnd).toMillis() / 2);
        long mid = windowStart.toEpochMilli() + Duration.between(windowStart, windowEnd).toMillis() / 2;
        double distance = Math.abs(departs.toEpochMilli() - mid) / (double) half;
        return clamp(1.0 - 0.6 * distance, 0.4, 1.0);
    }

    /** Snug fit scores higher so large trips are not wasted on tiny parcels. */
    public static double capacityFit(int weightGrams, int remainingGrams) {
        return remainingGrams <= 0 ? 0 : clamp(weightGrams / (double) remainingGrams);
    }

    /** Normalises a value across the candidate set to [0,1]; if all equal, everyone gets 1. */
    public static double normalise(double value, double min, double max, boolean higherIsBetter) {
        if (max - min < 1e-9) {
            return 1.0;
        }
        double n = (value - min) / (max - min);
        return higherIsBetter ? n : 1.0 - n;
    }

    private static double clamp(double v) {
        return clamp(v, 0, 1);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
