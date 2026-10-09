package com.parcelshare.payment;

/** All money is integer paise. Never use floating point for amounts. */
public final class Pricing {

    /** Minimum fee so very light parcels are still worth a traveller's trouble (Rs 50). */
    public static final long MIN_PRICE_PAISE = 5_000;

    private Pricing() {
    }

    public static long quote(int pricePerKgPaise, int weightGrams) {
        long raw = ((long) pricePerKgPaise * weightGrams + 999) / 1000; // round up
        return Math.max(MIN_PRICE_PAISE, raw);
    }

    public static long commission(long pricePaise, int percent) {
        return (pricePaise * percent + 50) / 100; // round half up
    }
}
