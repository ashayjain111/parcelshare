package com.parcelshare.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parcelshare.identity.Reputation;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MatchScorerTest {

    /** The worked example from the product spec: Delhi->Lucknow (5kg, 4.9, 98%) vs Noida->Lucknow (3kg, 4.7, 95%). */
    @Test
    void exactRouteHighRatedTravellerOutranksNearbyAlternative() {
        // Sender's parcel is Delhi -> Lucknow, 2 kg.
        var a = MatchScorer.score(new MatchScorer.Inputs(true, true, 1.0, 4.9, 0.98, 0.5, MatchScorer.capacityFit(2000, 5000)));
        var b = MatchScorer.score(new MatchScorer.Inputs(false, true, 1.0, 4.7, 0.95, 0.5, MatchScorer.capacityFit(2000, 3000)));
        assertTrue(a.score() > b.score(), "A=" + a.score() + " B=" + b.score());
    }

    @Test
    void weightsSumToOneHundred() {
        var perfect = MatchScorer.score(new MatchScorer.Inputs(true, true, 1, 5, 1, 1, 1));
        assertEquals(100, perfect.score());
    }

    @Test
    void timeFitIsBestAtWindowMiddle() {
        Instant start = Instant.parse("2030-01-01T00:00:00Z");
        Instant end = Instant.parse("2030-01-01T12:00:00Z");
        double mid = MatchScorer.timeFit(Instant.parse("2030-01-01T06:00:00Z"), start, end);
        double edge = MatchScorer.timeFit(end, start, end);
        assertEquals(1.0, mid, 1e-9);
        assertEquals(0.4, edge, 1e-9);
    }

    @Test
    void newcomerWithOneFiveStarDoesNotBeatAVeteran() {
        double newcomer = Reputation.smoothedRating(5, 1);
        double veteran = Reputation.smoothedRating(960, 200); // 4.8 average
        assertTrue(veteran > newcomer, "newcomer=" + newcomer + " veteran=" + veteran);
    }

    @Test
    void disputesHurtReliabilityMoreThanCancellations() {
        assertTrue(Reputation.reliability(10, 0, 1) < Reputation.reliability(10, 1, 0));
    }
}
