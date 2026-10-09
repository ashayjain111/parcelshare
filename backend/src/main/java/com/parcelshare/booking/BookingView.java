package com.parcelshare.booking;

import com.parcelshare.identity.UserSummary;
import com.parcelshare.parcel.ParcelView;
import com.parcelshare.trip.TripView;
import java.time.Instant;
import java.util.List;

/**
 * What a particular viewer is allowed to see. Secrets (delivery code, recipient link) go to the sender only;
 * phone numbers of the counterpart are revealed only once the traveller has accepted.
 */
public record BookingView(Long id, ParcelView parcel, TripView trip, UserSummary sender, UserSummary traveller,
                          String status, long pricePaise, long commissionPaise, long travellerPayoutPaise,
                          String paymentStatus, Instant releaseAt, String viewerRole, List<String> actions,
                          String deliveryCode, String recipientLink, String counterpartPhone, Instant handoverExpiresAt,
                          Instant createdAt) {
}
