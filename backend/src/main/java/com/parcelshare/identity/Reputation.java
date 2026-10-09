package com.parcelshare.identity;

/**
 * Bayesian-smoothed reputation maths so a brand-new user with one 5-star review does not
 * outrank a veteran with 200 reviews averaging 4.8.
 */
public final class Reputation {

    private static final double PRIOR_RATING = 3.8;
    private static final int PRIOR_RATING_WEIGHT = 5;
    private static final double PRIOR_RELIABILITY = 0.9;
    private static final int PRIOR_RELIABILITY_WEIGHT = 4;

    private Reputation() {
    }

    /** Average rating in [1,5], pulled toward the prior when there are few ratings. */
    public static double smoothedRating(int ratingSum, int ratingCount) {
        return (ratingSum + PRIOR_RATING * PRIOR_RATING_WEIGHT) / (ratingCount + PRIOR_RATING_WEIGHT);
    }

    /** Share of commitments honoured in [0,1]; disputes count against a user more than a cancellation. */
    public static double reliability(int completed, int cancelled, int disputed) {
        double good = completed + PRIOR_RELIABILITY * PRIOR_RELIABILITY_WEIGHT;
        double total = completed + cancelled + 2.0 * disputed + PRIOR_RELIABILITY_WEIGHT;
        return Math.max(0, Math.min(1, good / total));
    }
}
