package com.parcelshare.identity;

/** What other users are allowed to see about someone: no email, no phone. */
public record UserSummary(Long id, String name, double ratingAvg, int ratingCount, int completedDeliveries,
                          int reliabilityPercent, boolean verifiedTraveller) {

    public static UserSummary of(AppUser u) {
        return new UserSummary(u.getId(), u.getFullName(), Math.round(u.ratingAvg() * 10.0) / 10.0,
                u.getRatingCount(), u.getCompletedDeliveries(), u.reliabilityPercent(),
                u.getTravellerVerification() == AppUser.Verification.APPROVED);
    }
}
