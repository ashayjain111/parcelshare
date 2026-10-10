package com.parcelshare.identity;

/** Full view of the caller's own account. */
public record UserDto(Long id, String email, String phone, String fullName, String role, boolean emailVerified,
                      String travellerVerification, double ratingAvg, int ratingCount, int completedDeliveries,
                      int reliabilityPercent, boolean identityVerified, String identityProvider) {

    public static UserDto of(AppUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getPhone(), u.getFullName(), u.getRole().name(),
                u.isEmailVerified(), u.getTravellerVerification().name(), round(u.ratingAvg()),
                u.getRatingCount(), u.getCompletedDeliveries(), u.reliabilityPercent(), u.getIdentityVerifiedAt() != null,
                u.getIdentityProvider());
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
